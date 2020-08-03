package com.oracle.infy.wookiee.grpc

import java.net.InetAddress

import cats.effect.IO
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.model.Host
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.{Server, ServerServiceDefinition}
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class WookieeGrpcServer(private val server: Server, private val curatorFramework: CuratorFramework) {

  def shutdown(): IO[Unit] = {
    for {
      _ <- IO(curatorFramework.close())
      _ <- IO(server.shutdown())
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
      dispatcherEC: ExecutionContext,
      mainEC: ExecutionContext,
      blockingEC: ExecutionContext,
      dispatcherECThreads: Int,
      mainECThreads: Int
  ): IO[WookieeGrpcServer] = {
    IO {
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
        dispatcherEC,
        mainEC,
        blockingEC,
        dispatcherECThreads,
        mainECThreads
      )
    }
  }

  def startUnsafe(
      zkQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      dispatcherEC: ExecutionContext,
      mainEC: ExecutionContext,
      blockingEC: ExecutionContext,
      dispatcherECThreads: Int,
      mainECThreads: Int
  ): Future[WookieeGrpcServer] = {
    start(
      zkQuorum,
      discoveryPath,
      zookeeperRetryInterval,
      zookeeperMaxRetries,
      serverServiceDefinition,
      port,
      dispatcherEC,
      mainEC,
      blockingEC,
      dispatcherECThreads,
      mainECThreads
    ).unsafeToFuture()
  }

  def start(
      zkQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      localhost: Host,
      dispatcherEC: ExecutionContext,
      mainEC: ExecutionContext,
      blockingEC: ExecutionContext,
      dispatcherECThreads: Int,
      mainECThreads: Int
  ): IO[WookieeGrpcServer] = {
    for {
      server <- IO {
        NettyServerBuilder
          .forPort(port)
          .bossEventLoopGroup(eventLoopGroup(dispatcherEC, dispatcherECThreads))
          .workerEventLoopGroup(eventLoopGroup(mainEC, mainECThreads))
          .executor(scalaToJavaExecutor(mainEC))
          .addService(
            serverServiceDefinition
          )
          .build()
      }
      _ <- IO {
        server.start()
      }
      curator <- IO(
        curatorFramework(zkQuorum, blockingEC, exponentialBackoffRetry(zookeeperRetryInterval, zookeeperMaxRetries))
      )
      _ <- IO(curator.start())
      _ <- IO(registerInZookeeper(discoveryPath, curator, localhost))
    } yield new WookieeGrpcServer(server, curator)
  }

  def startUnsafe(
      zkQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      localhost: Host,
      dispatcherEC: ExecutionContext,
      mainEC: ExecutionContext,
      blockingEC: ExecutionContext,
      dispatcherECThreads: Int,
      mainECThreads: Int
  ): Future[WookieeGrpcServer] = {
    start(
      zkQuorum,
      discoveryPath,
      zookeeperRetryInterval,
      zookeeperMaxRetries,
      serverServiceDefinition,
      port,
      localhost,
      dispatcherEC,
      mainEC,
      blockingEC,
      dispatcherECThreads,
      mainECThreads
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
