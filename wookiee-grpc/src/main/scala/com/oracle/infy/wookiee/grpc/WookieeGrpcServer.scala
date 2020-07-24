package com.oracle.infy.wookiee.grpc

import java.net.InetAddress

import cats.effect.IO
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.model.Host
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.{Server, ServerBuilder, ServerInterceptors, ServerServiceDefinition}
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.CreateMode

import scala.concurrent.Future

class WookieeGrpcServer(private val server: Server) {

  def shutdown(): IO[Unit] = {
    IO {
      server.shutdown()
    }.map(_ => ())
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
      serverServiceDefinition: ServerServiceDefinition,
      port: Int
  ): IO[WookieeGrpcServer] = {
    IO {
      InetAddress.getLocalHost.getCanonicalHostName
    }.flatMap { address =>
      start(zkQuorum, discoveryPath, serverServiceDefinition, port, Host(0, address, port, Map("foo" -> "bar")))
    }
  }

  def startUnsafe(
      zkQuorum: String,
      discoveryPath: String,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int
  ): Future[WookieeGrpcServer] = {
    start(zkQuorum, discoveryPath, serverServiceDefinition, port).unsafeToFuture()
  }

  @SuppressWarnings(
    Array(
      "scalafix:DisableSyntax.asInstanceOf"
    )
  )
  def start(
      zkQuorum: String,
      discoveryPath: String,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      localhost: Host
  ): IO[WookieeGrpcServer] = {
    for {
      server <- IO {
        ServerBuilder
          .forPort(port)
          .addService(
            serverServiceDefinition
          )
          .asInstanceOf[NettyServerBuilder]
          .build()
      }
      _ <- IO {
        server.start()
      }
      _ <- IO(registerInZookeeper(discoveryPath, zkQuorum, localhost))

    } yield new WookieeGrpcServer(server)
  }

  def startUnsafe(
      zkQuorum: String,
      discoveryPath: String,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      localhost: Host
  ): Future[WookieeGrpcServer] = {
    start(zkQuorum, discoveryPath, serverServiceDefinition, port, localhost).unsafeToFuture()
  }

  private def registerInZookeeper(discoveryPath: String, zkConnectString: String, host: Host): Unit = {
    val curator = CuratorFrameworkFactory
      .builder()
      .connectString(zkConnectString)
      .retryPolicy(new ExponentialBackoffRetry(1000, 3000))
      .build()
    curator.start()
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
