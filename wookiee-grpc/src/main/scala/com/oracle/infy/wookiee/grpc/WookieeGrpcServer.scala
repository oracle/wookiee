package com.oracle.infy.wookiee.grpc

import java.net.InetAddress

import cats.effect.{Blocker, ContextShift, Fiber, IO, Timer}
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.model.Host
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.channel
import io.grpc.netty.shaded.io.netty.channel.ChannelFactory
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
        println(f)
        assignLoad(f, host, discoveryPath, curatorFramework)
      }
      .compile
      .drain
  }

  private def assignLoad(load: Int, host: Host, discoveryPath: String, curatorFramework: CuratorFramework): IO[Int] = {
    val newHost = Host(host.version, host.address, host.port, host.metadata.updated("load", load.toString))
    IO {
      val data: Either[HostSerde.HostSerdeError, Host] =
        HostSerde.deserialize(curatorFramework.getData.forPath(s"$discoveryPath/${host.address}:${host.port}"))
      println(data.getOrElse(0))
      curatorFramework
        .setData()
        .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
      val data2: Either[HostSerde.HostSerdeError, Host] = HostSerde.deserialize(
        curatorFramework.getData.forPath(s"$discoveryPath/${host.address}:${host.port}")
      )
      println(data2.getOrElse(0))
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
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext,
      bossThreads: Int,
      mainExecutionContextThreads: Int
  )(
      implicit cs: ContextShift[IO],
      blocker: Blocker,
      logger: Logger[IO],
      timer: Timer[IO]
  ): IO[WookieeGrpcServer] = {
    cs.blockOn(blocker)(IO {
      InetAddress.getLocalHost.getCanonicalHostName
    }.flatMap { address =>
      start(
        zookeeperQuorum,
        discoveryPath,
        zookeeperRetryInterval,
        zookeeperMaxRetries,
        serverServiceDefinition,
        port,
        Host(0, address, port, Map.empty),
        mainExecutionContext,
        blockingExecutionContext,
        bossThreads,
        mainExecutionContextThreads,
        Queue.unbounded[IO, Int].unsafeRunSync()
      )
    })
  }

  def startUnsafe(
      zookeeperQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext,
      timerEC: ExecutionContext,
      bossThreads: Int,
      mainExecutionContextThreads: Int
  ): Future[WookieeGrpcServer] = {

    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingExecutionContext)
    implicit val cs: ContextShift[IO] = IO.contextShift(mainExecutionContext)
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()
    implicit val timer: Timer[IO] = IO.timer(timerEC)

    start(
      zookeeperQuorum,
      discoveryPath,
      zookeeperRetryInterval,
      zookeeperMaxRetries,
      serverServiceDefinition,
      port,
      mainExecutionContext,
      blockingExecutionContext,
      bossThreads,
      mainExecutionContextThreads
    ).unsafeToFuture()
  }

  def start(
      zookeeperQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      localhost: Host,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext,
      bossThreads: Int,
      mainExecutionContextThreads: Int,
      queue: Queue[IO, Int]
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
          .channelFactory(new ChannelFactory[channel.ServerChannel] {
            override def newChannel(): channel.ServerChannel = new NioServerSocketChannel()
          })
          .bossEventLoopGroup(eventLoopGroup(blockingExecutionContext, bossThreads))
          .workerEventLoopGroup(eventLoopGroup(mainExecutionContext, mainExecutionContextThreads))
          .executor(scalaToJavaExecutor(mainExecutionContext))
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
            blockingExecutionContext,
            exponentialBackoffRetry(zookeeperRetryInterval, zookeeperMaxRetries)
          )
        )
      )
      _ <- cs.blockOn(blocker)(IO(curator.start()))
      _ <- logger.info("Registering gRPC server in zookeeper...")
      _ <- cs.blockOn(blocker)(IO(registerInZookeeper(discoveryPath, curator, localhost)))
      fiber <- streamLoads(queue, localhost, discoveryPath, curator).start

    } yield new WookieeGrpcServer(server, curator, fiber, queue)
  }

  def startUnsafe(
      zookeeperQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      localhost: Host,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext,
      timerEC: ExecutionContext,
      bossThreads: Int,
      mainExecutionContextThreads: Int,
      queue: Option[Queue[IO, Int]]
  ): Future[WookieeGrpcServer] = {
    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingExecutionContext)
    implicit val cs: ContextShift[IO] = IO.contextShift(mainExecutionContext)
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()
    implicit val timer: Timer[IO] = IO.timer(timerEC)
    start(
      zookeeperQuorum,
      discoveryPath,
      zookeeperRetryInterval,
      zookeeperMaxRetries,
      serverServiceDefinition,
      port,
      localhost,
      mainExecutionContext,
      blockingExecutionContext,
      bossThreads,
      mainExecutionContextThreads,
      queue.get
    ).unsafeToFuture()
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
