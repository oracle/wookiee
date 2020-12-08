package com.oracle.infy.wookiee.grpc

import java.util.concurrent.ForkJoinPool

import cats.effect.{Blocker, ContextShift, Fiber, IO, Timer}
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.json.{HostSerde, ServerSettings}
import com.oracle.infy.wookiee.model.Host
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel
import io.grpc.{Server, ServerServiceDefinition}
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class WookieeGrpcServer(
    private val server: Server,
    private val curatorFramework: CuratorFramework,
    private val fiber: Fiber[IO, Unit],
    private val loadQueue: Queue[IO, Int]
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
      serverSettings.queue
    ).unsafeToFuture()
  }

  private def streamLoads(
      loadQueue: Queue[IO, Int],
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework
  )(implicit timer: Timer[IO], cs: ContextShift[IO]): IO[Unit] = {
    val stream: Stream[IO, Int] = loadQueue.dequeue
    stream
      .debounce(100.millis)
      .evalTap { f: Int =>
        assignLoad(f, host, discoveryPath, curatorFramework)
      }
      .compile
      .drain
  }

  private def assignLoad(load: Int, host: Host, discoveryPath: String, curatorFramework: CuratorFramework): IO[Int] = {
    val newHost = Host(host.version, host.address, host.port, host.metadata.updated("load", load.toString))
    IO {
      curatorFramework
        .setData()
        .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
      load
    }
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
      queueIO: IO[Queue[IO, Int]]
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
      _ <- cs.blockOn(blocker)(IO(registerInZookeeper(discoveryPath, curator, host)))
      fiber <- streamLoads(queue, host, discoveryPath, curator).start

    } yield new WookieeGrpcServer(server, curator, fiber, queue)
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
