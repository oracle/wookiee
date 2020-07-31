package com.oracle.infy.wookiee.grpc.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import cats.data.EitherT
import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Blocker, Concurrent, ContextShift, IO}
import cats.implicits.{catsSyntaxEq => _, _}
import com.oracle.infy.wookiee.grpc.contract.{CloseableStreamContract, HostnameServiceContract}
import com.oracle.infy.wookiee.grpc.errors.Errors
import com.oracle.infy.wookiee.grpc.errors.Errors.{UnknownWookieeGrpcError, WookieeGrpcError}
import com.oracle.infy.wookiee.grpc.impl.ZookeeperHostnameService._
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.model.Host
import com.oracle.infy.wookiee.utils.implicits._
import fs2._
import io.chrisdavenport.log4cats.Logger
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.{ChildData, CuratorCache, CuratorCacheListener}

protected[grpc] object ZookeeperHostnameService {

  sealed trait CachedNodeReference {
    def mzxid: Long
  }

  final case class NodeData(data: Host, mzxid: Long) extends CachedNodeReference

  final case class Tombstone(mzxid: Long) extends CachedNodeReference

}

protected[grpc] class ZookeeperHostnameService(
    curatorRef: Ref[IO, CuratorFramework],
    cacheRef: Ref[IO, Option[CuratorCache]],
    s: Semaphore[IO],
    closableStream: CloseableStreamContract[IO, Set[Host], Stream],
    pushHosts: Set[Host] => IO[Unit]
)(implicit blocker: Blocker, cs: ContextShift[IO], concurrent: Concurrent[IO], logger: Logger[IO])
    extends HostnameServiceContract[IO, Stream] {

  override def shutdown: EitherT[IO, Errors.WookieeGrpcError, Unit] = {
    val closeZKResources = (for {
      curator <- curatorRef.get
      cache <- cacheRef.get
      _ <- cs.blockOn(blocker)(IO(cache.map(_.close()).getOrElse(())))
      _ <- cs.blockOn(blocker)(IO(curator.close()))
    } yield ())
      .toEitherT(t => UnknownWookieeGrpcError(t.getMessage))

    val shutdownStream = closableStream.shutdown().leftMap(err => UnknownWookieeGrpcError(err.toString))

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

    val hasInitialized = new AtomicBoolean(false)
    val state = new ConcurrentHashMap[String, CachedNodeReference]()

    val computation = for {
      curator <- curatorRef.get
      _ <- cs.blockOn(blocker)(IO(curator.start()))
      _ <- logger.info("GRPC Service Discovery curator has started")
      cache <- cs.blockOn(blocker)(
        IO(
          CuratorCache
            .build(curator, rootPath)
        )
      )
      _ <- cs.blockOn(blocker)(
        IO(
          cache
            .listenable()
            .addListener(cacheListener(hasInitialized, state, pushHosts, rootPath))
        )
      )
      _ <- cacheRef.set(Some(cache))
      _ <- cs.blockOn(blocker)(IO(cache.start()))
      _ <- logger.info("GRPC Service Discovery curator cache has started")
    } yield {
      closableStream
    }

    s.acquire
      .bracket(_ => computation)(_ => s.release)
      .toEitherT(t => UnknownWookieeGrpcError(t.getMessage))
  }

  private def cacheListener(
      hasInitialized: AtomicBoolean,
      state: ConcurrentHashMap[String, CachedNodeReference],
      pushHosts: Set[Host] => IO[Unit],
      rootPath: String
  ): CuratorCacheListener = {
    CuratorCacheListener
      .builder
      .forCreates((node: ChildData) => {
        addOrUpdateNodeState(node, state, rootPath)
        if (hasInitialized.get()) {
          sendHosts(pushHosts, state)
        }
      })
      .forChanges(
        (_: ChildData, node: ChildData) => {
          addOrUpdateNodeState(node, state, rootPath)
          if (hasInitialized.get()) {
            sendHosts(pushHosts, state)
          }
        }
      )
      .forDeletes((oldNode: ChildData) => {
        deleteNodeState(oldNode, state, rootPath)
        if (hasInitialized.get()) {
          sendHosts(pushHosts, state)
        }
      })
      .forInitialized(() => {
        logger.info("State has been initialized. All nodes read in from zookeeper")
        hasInitialized.set(true)
        sendHosts(pushHosts, state)
      })
      .build
  }

  private def sendHosts(
      pushHosts: Set[Host] => IO[Unit],
      state: ConcurrentHashMap[String, CachedNodeReference]
  ): Unit = {
    logger.info(s"Sending hosts on stream: $state").unsafeRunSync()
    pushHosts(toHostList(state)).unsafeRunSync()
  }

  private def toHostList(state: ConcurrentHashMap[String, CachedNodeReference]): Set[Host] = {
    state
      .valueSet
      .collect {
        case NodeData(data, _) => data
      }
  }

  private def addOrUpdateNodeState(
      zkData: ChildData,
      state: ConcurrentHashMap[String, CachedNodeReference],
      rootPath: String
  ): ConcurrentHashMap[String, CachedNodeReference] = {
    if (zkData.getPath =/= rootPath) {
      HostSerde.deserialize(zkData.getData) match {
        // TODO: Healthcheck should go into degraded state
        case Left(err) => logger.error(s"Unable to parse host data from zookeeper: $err").unsafeRunSync()
        case Right(host) =>
          Option(state.get(zkData.getPath)) match {
            case Some(cachedData) =>
              if (zkData.getStat.getMzxid > cachedData.mzxid) {
                logger.info(s"Replacing cached node data $cachedData with new host: $host")
                state.put(zkData.getPath, NodeData(host, zkData.getStat.getMzxid))
              }
            case None =>
              logger.info(s"Storing new host in map: $host")
              state.put(zkData.getPath, NodeData(host, zkData.getStat.getMzxid))
          }
      }
    }
    state
  }

  def deleteNodeState(
      zkData: ChildData,
      state: ConcurrentHashMap[String, CachedNodeReference],
      rootPath: String
  ): ConcurrentHashMap[String, CachedNodeReference] = {
    if (zkData.getPath =/= rootPath) {

      Option(state.get(zkData.getPath)) match {
        case Some(cachedData) =>
          logger.info(s"Putting tombstone in place of: $cachedData")
          // must check greater than or equal on deletes because zxid of delete event is not stored on ChildData
          if (zkData.getStat.getMzxid >= cachedData.mzxid) {
            state.put(zkData.getPath, Tombstone(zkData.getStat.getMzxid))
          }
        case None =>
          logger.info(s"Putting tombstone on path ${zkData.getPath}")
          state.put(zkData.getPath, Tombstone(zkData.getStat.getMzxid))
      }
    }

    state
  }

}
