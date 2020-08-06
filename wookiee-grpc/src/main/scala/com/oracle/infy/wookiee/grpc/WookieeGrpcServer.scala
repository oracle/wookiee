package com.oracle.infy.wookiee.grpc

import java.net.InetAddress

import cats.effect.{Blocker, ContextShift, IO}
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.model.Host
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

class WookieeGrpcServer(private val server: Server, private val curatorFramework: CuratorFramework)(
    implicit cs: ContextShift[IO],
    logger: Logger[IO],
    blocker: Blocker
) {

  def shutdown(): IO[Unit] = {
    for {
      _ <- logger.info("Closing curator client")
      _ <- cs.blockOn(blocker)(IO(curatorFramework.close()))
      _ <- logger.info("Shutting down gRPC server...")
      _ <- cs.blockOn(blocker)(IO(server.shutdown()))
      _ <- cs.blockOn(blocker)(IO(server.awaitTermination()))
    } yield ()
  }

  def awaitTermination(): Unit = {
    server.awaitTermination()
  }

  def shutdownUnsafe(): Future[Unit] = {
    shutdown().unsafeToFuture()
  }

}

object WookieeGrpcServer {

  def start(
      zkQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      mainEC: ExecutionContext,
      blockingEC: ExecutionContext,
      bossThreads: Int,
      mainECThreads: Int
  )(
      implicit cs: ContextShift[IO],
      blocker: Blocker,
      logger: Logger[IO]
  ): IO[WookieeGrpcServer] = {
    cs.blockOn(blocker)(IO {
      InetAddress.getLocalHost.getCanonicalHostName
    }.flatMap { address =>
      start(
        zkQuorum,
        discoveryPath,
        zookeeperRetryInterval,
        zookeeperMaxRetries,
        serverServiceDefinition,
        port,
        Host(0, address, port, Map.empty),
        mainEC,
        blockingEC,
        bossThreads,
        mainECThreads
      )
    })
  }

  def startUnsafe(
      zkQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext,
      bossThreads: Int,
      mainThreads: Int
  ): Future[WookieeGrpcServer] = {

    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingExecutionContext)
    implicit val cs: ContextShift[IO] = IO.contextShift(mainExecutionContext)
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()

    start(
      zkQuorum,
      discoveryPath,
      zookeeperRetryInterval,
      zookeeperMaxRetries,
      serverServiceDefinition,
      port,
      mainExecutionContext,
      blockingExecutionContext,
      bossThreads,
      mainThreads
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
      mainExecutionContextThreads: Int
  )(
      implicit cs: ContextShift[IO],
      blocker: Blocker,
      logger: Logger[IO]
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
    } yield new WookieeGrpcServer(server, curator)
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
      bossThreads: Int,
      mainExecutionContextThreads: Int
  ): Future[WookieeGrpcServer] = {
    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingExecutionContext)
    implicit val cs: ContextShift[IO] = IO.contextShift(mainExecutionContext)
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()
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
      mainExecutionContextThreads
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
