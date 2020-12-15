package com.oracle.infy.wookiee.grpc

import java.util.concurrent.ForkJoinPool

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ContextShift, Fiber, IO, Timer}
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.json.{HostSerde, ServerSettings}
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel
import io.grpc.{Server, ServerServiceDefinition}
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.data.Stat

import scala.concurrent.duration._
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

  def enterQuarantine(): IO[Stat] = {
    quarantined.getAndSet(true)
    WookieeGrpcServer.assignQuarantine(isQuarantined = true, host, discoveryPath, curatorFramework)
  }

  def exitQuarantine(): IO[Stat] = {
    quarantined.getAndSet(false)
    WookieeGrpcServer.assignQuarantine(isQuarantined = false, host, discoveryPath, curatorFramework)
  }

}

object WookieeGrpcServer {

  def createExecutionContext(parallelism: Int): ExecutionContext = {
    ExecutionContext.fromExecutor(
      new ForkJoinPool(
        parallelism,
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        (t: Thread, e: Throwable) => { println(s"Got an uncaught exception on thread: $e" ++ t.getName) },
        true
      )
    )
  }

  // Wrapper for streamLoads: map IO to Boolean and use to verify that server is not in quarantined state before using.
  private def streamLoads(
      loadQueue: Queue[IO, Int],
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework,
      quarantineRef: Ref[IO, Boolean]
  )(implicit timer: Timer[IO], cs: ContextShift[IO]): IO[Unit] = {
    for {
      quarantined <- quarantineRef.get
      result <- streamLoads(loadQueue, host, discoveryPath, curatorFramework, quarantined)
    } yield result
  }

  private def streamLoads(
      queue: Queue[IO, Int],
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework,
      quarantined: Boolean
  )(implicit timer: Timer[IO], cs: ContextShift[IO]): IO[Unit] = {
    if (!quarantined) {
      val stream: Stream[IO, Int] = queue.dequeue
      stream
        .debounce(100.millis)
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
  ): IO[Stat] = {
    IO {
      val newHost = Host(host.version, host.address, host.port, HostMetadata(load, host.metadata.quarantined))
      curatorFramework
        .setData()
        .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
    }
  }

  private def assignQuarantine(
      isQuarantined: Boolean,
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework
  ): IO[Stat] = {
    IO {
      val newHost = Host(host.version, host.address, host.port, HostMetadata(host.metadata.load, isQuarantined))
      curatorFramework
        .setData()
        .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
    }
  }

  def startUnsafe(serverSettings: ServerSettings): Future[WookieeGrpcServer] = {
    implicit val blocker: Blocker = Blocker.liftExecutionContext(serverSettings.zookeeperBlockingExecutionContext)
    implicit val cs: ContextShift[IO] = IO.contextShift(serverSettings.bossExecutionContext)
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()
    implicit val timer: Timer[IO] = IO.timer(serverSettings.timerExecutionContext)
    start(
      serverSettings.zookeeperQuorum,
      serverSettings.discoveryPath,
      serverSettings.zookeeperRetryInterval,
      serverSettings.zookeeperMaxRetries,
      serverSettings.serverServiceDefinition,
      serverSettings.port,
      serverSettings.host,
      serverSettings.bossExecutionContext,
      serverSettings.workerExecutionContext,
      serverSettings.applicationExecutionContext,
      serverSettings.zookeeperBlockingExecutionContext,
      serverSettings.bossThreads,
      serverSettings.workerThreads,
      serverSettings.queue,
      serverSettings.quarantined
    ).unsafeToFuture()
  }

  def start(
      zookeeperQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      localhost: IO[Host],
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      zookeeperBlockingExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int,
      queueIO: IO[Queue[IO, Int]],
      quarantinedRef: IO[Ref[IO, Boolean]]
  )(
      implicit cs: ContextShift[IO],
      blocker: Blocker,
      logger: Logger[IO],
      timer: Timer[IO]
  ): IO[WookieeGrpcServer] = {
    for {
      server <- cs.blockOn(blocker)(IO {
        NettyServerBuilder
          .forPort(port)
          .channelFactory(() => new NioServerSocketChannel())
          .bossEventLoopGroup(eventLoopGroup(bossExecutionContext, bossThreads))
          .workerEventLoopGroup(eventLoopGroup(workerExecutionContext, workerThreads))
          .executor(scalaToJavaExecutor(applicationExecutionContext))
          .addService(
            serverServiceDefinition
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
            zookeeperQuorum,
            zookeeperBlockingExecutionContext,
            exponentialBackoffRetry(zookeeperRetryInterval, zookeeperMaxRetries)
          )
        )
      )
      _ <- cs.blockOn(blocker)(IO(curator.start()))
      _ <- logger.info("Registering gRPC server in zookeeper...")
      host <- localhost
      queue <- queueIO
      quarantined <- quarantinedRef
      // Create an object that stores whether or not the server is quarantined.
      _ <- cs.blockOn(blocker)(IO(registerInZookeeper(discoveryPath, curator, host)))
      fiber <- streamLoads(queue, host, discoveryPath, curator, quarantined).start

    } yield new WookieeGrpcServer(server, curator, fiber, queue, host, discoveryPath, quarantined)
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
