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
//import fs2.Stream

class WookieeGrpcServer(
    private val server: Server,
    private val curatorFramework: CuratorFramework,
    private val host: Host,
    private val discoveryPath: String
)(
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
    } yield ()
  }

  def awaitTermination(): IO[Unit] = {
    cs.blockOn(blocker)(IO(server.awaitTermination()))
  }

  // todo: add a method that takes a stream as an argument and calls assignLoad repeatedly, then batches the calls
  //  to zk as a stream

  def shutdownUnsafe(): Future[Unit] = {
    shutdown().unsafeToFuture()
  }

  def assignLoad(load: Int): IO[Unit] = { // todo: I think this will need the discovery path and hostname...otherwise
    val newHost = Host(host.version, host.address, host.port, host.metadata.updated("load", load.toString)) // todo: should version be updated here?
    curatorFramework
      .setData()
      .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
    IO(())
  }

  def unsafeAssignLoad(load: Int): Future[Unit] = {
    assignLoad(load).unsafeToFuture()
  }

}

object WookieeGrpcServer {

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
      logger: Logger[IO]
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
        Host(0, address, port, Map.empty), //todo: this should probably be modified to add load so it can be tracked
        mainExecutionContext,
        blockingExecutionContext,
        bossThreads,
        mainExecutionContextThreads
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
    } yield new WookieeGrpcServer(server, curator, localhost, discoveryPath)
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
      mainExecutionContextThreads: Int //TODO: add load number here? not sure if zookeeper actually needs to know the load number
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
