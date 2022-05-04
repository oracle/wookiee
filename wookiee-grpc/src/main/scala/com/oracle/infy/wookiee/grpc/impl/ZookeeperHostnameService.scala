package com.oracle.infy.wookiee.grpc.impl

import cats.data.EitherT
import cats.effect.std.{Dispatcher, Semaphore}
import cats.effect.{IO, Ref}
import cats.implicits.{catsSyntaxEq => _, _}
import com.oracle.infy.wookiee.grpc.contract.{CloseableStreamContract, HostnameServiceContract}
import com.oracle.infy.wookiee.grpc.errors.Errors
import com.oracle.infy.wookiee.grpc.errors.Errors._
import com.oracle.infy.wookiee.grpc.impl.ZookeeperHostnameService._
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.grpc.model.Host
import com.oracle.infy.wookiee.grpc.utils.implicits._
import fs2._
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.{ChildData, CuratorCache, CuratorCacheListener}
import org.typelevel.log4cats.Logger

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

protected[grpc] object ZookeeperHostnameService {

  sealed trait CachedNodeReference {
    def mzxid: Long
  }

  final case class NodeData(data: Host, mzxid: Long) extends CachedNodeReference

  final case class Tombstone(mzxid: Long) extends CachedNodeReference

}

protected[grpc] class ZookeeperHostnameService(
    curator: CuratorFramework,
    cacheRef: Ref[IO, Option[CuratorCache]],
    s: Semaphore[IO],
    closableStream: CloseableStreamContract[IO, Set[Host], Stream],
    pushHosts: Set[Host] => IO[Unit]
)(implicit dispatcher: Dispatcher[IO], logger: Logger[IO])
    extends HostnameServiceContract[IO, Stream] {

  override def shutdown: EitherT[IO, Errors.WookieeGrpcError, Unit] = {
    val closeZKResources = (for {
      cache <- cacheRef.get
      _ <- IO.blocking(cache.map(_.close()).getOrElse(()))
    } yield ())
      .toEitherT(t => UnknownCuratorShutdownError(t.stackTrace): WookieeGrpcError)

    val shutdownStream = closableStream.shutdown().leftMap(err => UnknownShutdownError(err.toString): WookieeGrpcError)

    EitherT(
      s.acquire
        .bracket(_ => (closeZKResources *> shutdownStream).value)(_ => {
          s.release.flatMap { _ =>
            logger.info("Zookeeper Hostname Service has been shutdown")
          }
        })
    )
  }

  override def hostStream(
      rootPath: String
  ): EitherT[IO, WookieeGrpcError, CloseableStreamContract[IO, Set[Host], Stream]] = {

    val lock = new Object
    val hasInitialized = new AtomicBoolean(false)
    val state = new ConcurrentHashMap[String, CachedNodeReference]()

    val computation = for {
      _ <- logger.info(s"GRPC Service Discovery has started... Looking for services under path $rootPath")
      cache <- IO.blocking(
        CuratorCache
          .build(curator, rootPath)
      )
      _ <- IO.blocking(
        cache
          .listenable()
          .addListener(cacheListener(lock, hasInitialized, state, pushHosts, rootPath))
      )
      _ <- cacheRef.set(Some(cache))
      _ <- IO.blocking(cache.start())
      _ <- logger.info("GRPC Service Discovery curator cache has started")
    } yield {
      closableStream
    }

    s.acquire
      .bracket(_ => computation)(_ => s.release)
      .toEitherT(t => UnknownHostStreamError(t.stackTrace))
  }

  private def cacheListener(
      lock: Object,
      hasInitialized: AtomicBoolean,
      state: ConcurrentHashMap[String, CachedNodeReference],
      pushHosts: Set[Host] => IO[Unit],
      rootPath: String
  ): CuratorCacheListener =
    CuratorCacheListener
      .builder
      .forCreates((node: ChildData) => {
        lock.synchronized {
          addOrUpdateNodeState(node, state, rootPath)
          if (hasInitialized.get()) {
            sendHosts(pushHosts, state)
          }
        }
      })
      .forChanges(
        (_: ChildData, node: ChildData) => {
          lock.synchronized {
            addOrUpdateNodeState(node, state, rootPath)
            if (hasInitialized.get()) {
              sendHosts(pushHosts, state)
            }
          }
        }
      )
      .forDeletes((oldNode: ChildData) => {
        lock.synchronized {
          deleteNodeState(oldNode, state, rootPath)
          if (hasInitialized.get()) {
            sendHosts(pushHosts, state)
          }
        }
      })
      .forInitialized(() => {
        lock.synchronized {
          dispatcher.unsafeRunSync(
            logger
              .info(
                s"State has been initialized. All nodes read in from zookeeper: ${toHostList(state)}"
              )
          )
          hasInitialized.set(true)
          sendHosts(pushHosts, state)
        }
      })
      .build

  private def sendHosts(
      pushHosts: Set[Host] => IO[Unit],
      state: ConcurrentHashMap[String, CachedNodeReference]
  ): Unit = {
    dispatcher.unsafeRunSync(logger.info(s"Sending hosts on stream: $state"))
    dispatcher.unsafeRunSync(pushHosts(toHostList(state)))
  }

  private def toHostList(state: ConcurrentHashMap[String, CachedNodeReference]): Set[Host] =
    state
      .valueSet
      .collect {
        case NodeData(data, _) => data
      }

  private def addOrUpdateNodeState(
      zkData: ChildData,
      state: ConcurrentHashMap[String, CachedNodeReference],
      rootPath: String
  ): ConcurrentHashMap[String, CachedNodeReference] = {
    if (zkData.getPath =/= rootPath) {
      HostSerde.deserialize(zkData.getData) match {
        // TODO: Healthcheck should go into degraded state
        case Left(err) =>
          dispatcher.unsafeRunSync(logger.error(s"Unable to parse host data from zookeeper: $err"))
        case Right(host) =>
          Option(state.get(zkData.getPath)) match {
            case Some(cachedData) =>
              if (zkData.getStat.getMzxid > cachedData.mzxid) {
                dispatcher.unsafeRunSync(logger.info(s"Replacing cached node data $cachedData with new host: $host"))
                state.put(zkData.getPath, NodeData(host, zkData.getStat.getMzxid))
              }
            case None =>
              dispatcher.unsafeRunSync(logger.info(s"Storing new host in map: $host"))
              state.put(zkData.getPath, NodeData(host, zkData.getStat.getMzxid))
          }
      }
    }
    state
  }

  private def deleteNodeState(
      zkData: ChildData,
      state: ConcurrentHashMap[String, CachedNodeReference],
      rootPath: String
  ): ConcurrentHashMap[String, CachedNodeReference] = {
    if (zkData.getPath =/= rootPath) {

      Option(state.get(zkData.getPath)) match {
        case Some(cachedData) =>
          dispatcher.unsafeRunSync(logger.info(s"Putting tombstone in place of: $cachedData"))
          // must check greater than or equal on deletes because zxid of delete event is not stored on ChildData
          if (zkData.getStat.getMzxid >= cachedData.mzxid) {
            state.put(zkData.getPath, Tombstone(zkData.getStat.getMzxid))
          }
        case None =>
          dispatcher.unsafeRunSync(logger.info(s"Putting tombstone on path ${zkData.getPath}"))
          state.put(zkData.getPath, Tombstone(zkData.getStat.getMzxid))
      }
    }

    state
  }

}
