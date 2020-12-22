package com.oracle.infy.wookiee.grpc

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ContextShift, Fiber, IO, Timer}
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.grpc.settings.ServerSettings
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode

import scala.concurrent.{ExecutionContext, Future}

class WookieeGrpcServer(
    private val server: Server,
    private val curatorFramework: CuratorFramework,
    private val fiber: Fiber[IO, Unit],
    private val loadQueue: Queue[IO, Int],
    private val host: Host,
    private val discoveryPath: String,
    private val quarantined: Ref[IO, Boolean]
)(
    implicit cs: ContextShift[IO],
    logger: Logger[IO],
    blocker: Blocker
) {

  def shutdown(): IO[Unit] = {
    for {
      _ <- logger.info("Stopping load writing process")
      _ <- fiber.cancel
      _ <- logger.info("Closing curator client")
      _ <- cs.blockOn(blocker)(IO(curatorFramework.close()))
      _ <- logger.info("Shutting down gRPC server...")
      _ <- cs.blockOn(blocker)(IO(server.shutdown()))
    } yield ()
  }

  def awaitTermination(): IO[Unit] = {
    cs.blockOn(blocker)(IO(server.awaitTermination()))
  }

  def shutdownUnsafe(): Future[Unit] = {
    shutdown().unsafeToFuture()
  }

  def assignLoad(load: Int): IO[Unit] = {
    loadQueue.enqueue1(load)
  }

  def unsafeAssignLoad(
      load: Int
  ): Future[Unit] = {
    assignLoad(load).unsafeToFuture()
  }

  def enterQuarantine(): IO[Unit] = {
    quarantined.getAndSet(true)
    WookieeGrpcServer.assignQuarantine(isQuarantined = true, host, discoveryPath, curatorFramework)
  }

  def exitQuarantine(): IO[Unit] = {
    quarantined.getAndSet(false)
    WookieeGrpcServer.assignQuarantine(isQuarantined = false, host, discoveryPath, curatorFramework)
  }

}

object WookieeGrpcServer {

  // Wrapper for streamLoads: map IO to Boolean and use to verify that server is not in quarantined state before using.
  private def streamLoads(
      loadQueue: Queue[IO, Int],
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework,
      serverSettings: ServerSettings,
      quarantineRef: Ref[IO, Boolean]
  )(implicit timer: Timer[IO], cs: ContextShift[IO], logger: Logger[IO]): IO[Unit] = {
    for {
      quarantined <- quarantineRef.get
      result <- streamLoads(loadQueue, host, discoveryPath, curatorFramework, serverSettings, quarantined)
    } yield result
  }

  private def streamLoads(
      queue: Queue[IO, Int],
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework,
      serverSettings: ServerSettings,
      quarantined: Boolean
  )(implicit timer: Timer[IO], cs: ContextShift[IO], logger: Logger[IO]): IO[Unit] = {
    if (!quarantined) {
      val stream: Stream[IO, Int] = queue.dequeue
      stream
        .debounce(serverSettings.loadUpdateInterval)
        .evalTap{ load =>
          logger.info(s"Writing load to zookeeper: load = $load")
        }
        .evalTap { load: Int =>
          assignLoad(load, host, discoveryPath, curatorFramework)
        }
        .compile
        .drain
    } else {
      IO(())
    }
  }

  private def assignLoad(
      load: Int,
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework
  ): IO[Unit] = {
    IO {
      val newHost = Host(host.version, host.address, host.port, HostMetadata(load, host.metadata.quarantined))
      curatorFramework
        .setData()
        .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
      ()
    }
  }

  private def assignQuarantine(
      isQuarantined: Boolean,
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework
  ): IO[Unit] = {
    IO {
      val newHost = Host(host.version, host.address, host.port, HostMetadata(host.metadata.load, isQuarantined))
      curatorFramework
        .setData()
        .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
      ()
    }
  }

  def startUnsafe(
      serverSettings: ServerSettings,
      timerExecutionContext: ExecutionContext
  ): Future[WookieeGrpcServer] = {
    implicit val blocker: Blocker = Blocker.liftExecutionContext(serverSettings.zookeeperBlockingExecutionContext)
    implicit val cs: ContextShift[IO] = IO.contextShift(serverSettings.bossExecutionContext)
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()
    implicit val timer: Timer[IO] = IO.timer(timerExecutionContext)
    start(serverSettings).unsafeToFuture()
  }

  def start(serverSettings: ServerSettings)(
      implicit cs: ContextShift[IO],
      blocker: Blocker,
      logger: Logger[IO],
      timer: Timer[IO]
  ): IO[WookieeGrpcServer] = {
    for {
      server <- cs.blockOn(blocker)(IO {
        NettyServerBuilder
          .forPort(serverSettings.port)
          .channelFactory(() => new NioServerSocketChannel())
          .bossEventLoopGroup(eventLoopGroup(serverSettings.bossExecutionContext, serverSettings.bossThreads))
          .workerEventLoopGroup(eventLoopGroup(serverSettings.workerExecutionContext, serverSettings.workerThreads))
          .executor(scalaToJavaExecutor(serverSettings.applicationExecutionContext))
          .addService(
            serverSettings.serverServiceDefinition
          )
          .build()
      })
      _ <- cs.blockOn(blocker)(IO {
        server.start()
      })
      _ <- logger.info("gRPC server started...")
      curator <- cs.blockOn(blocker)(
        IO(
          curatorFramework(
            serverSettings.zookeeperQuorum,
            serverSettings.zookeeperBlockingExecutionContext,
            exponentialBackoffRetry(serverSettings.zookeeperRetryInterval, serverSettings.zookeeperMaxRetries)
          )
        )
      )
      _ <- cs.blockOn(blocker)(IO(curator.start()))
      _ <- logger.info("Registering gRPC server in zookeeper...")
      host <- serverSettings.host
      queue <- serverSettings.queue
      quarantined <- serverSettings.quarantined
      // Create an object that stores whether or not the server is quarantined.
      _ <- cs.blockOn(blocker)(IO(registerInZookeeper(serverSettings.discoveryPath, curator, host)))
      fiber <- streamLoads(queue, host, serverSettings.discoveryPath, curator, serverSettings, quarantined).start

    } yield new WookieeGrpcServer(server, curator, fiber, queue, host, serverSettings.discoveryPath, quarantined)
  }

  private def registerInZookeeper(
      discoveryPath: String,
      curator: CuratorFramework,
      host: Host
  ): Unit = {

    if (Option(curator.checkExists().forPath(discoveryPath)).isEmpty) {
      curator
        .create()
        .orSetData()
        .creatingParentsIfNeeded()
        .forPath(discoveryPath)
    }
    curator
      .create
      .orSetData()
      .withMode(CreateMode.EPHEMERAL)
      .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(host))
    ()
  }
}
