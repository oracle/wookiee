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
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode

final class WookieeGrpcServer(
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
      _ <- logger.info("Stopping load writing process...")
      _ <- fiber.cancel
      _ <- logger.info("Shutting down gRPC server...")
      _ <- cs.blockOn(blocker)(IO(server.shutdown()))
    } yield ()
  }

  def awaitTermination(): IO[Unit] = {
    cs.blockOn(blocker)(IO(server.awaitTermination()))
  }

  def assignLoad(load: Int): IO[Unit] = {
    loadQueue.enqueue1(load)
  }

  def enterQuarantine(): IO[Unit] = {
    quarantined
      .getAndSet(true)
      .*>(
        WookieeGrpcServer.assignQuarantine(isQuarantined = true, host, discoveryPath, curatorFramework)
      )
  }

  def exitQuarantine(): IO[Unit] = {
    quarantined
      .getAndSet(false)
      .*>(
        WookieeGrpcServer.assignQuarantine(isQuarantined = false, host, discoveryPath, curatorFramework)
      )
  }

}

object WookieeGrpcServer {

  def start(serverSettings: ServerSettings)(
      implicit cs: ContextShift[IO],
      blocker: Blocker,
      logger: Logger[IO],
      timer: Timer[IO]
  ): IO[WookieeGrpcServer] = {
    for {
      host <- serverSettings.host
      server <- cs.blockOn(blocker)(IO {
        var builder = NettyServerBuilder
          .forPort(host.port)
          .channelFactory(() => new NioServerSocketChannel())
          .bossEventLoopGroup(eventLoopGroup(serverSettings.bossExecutionContext, serverSettings.bossThreads))
          .workerEventLoopGroup(eventLoopGroup(serverSettings.workerExecutionContext, serverSettings.workerThreads))
          .executor(scalaToJavaExecutor(serverSettings.applicationExecutionContext))

        serverSettings.serverServiceDefinitions.map { service =>
          builder = builder.addService(service)
        }

        builder.build()
      })
      _ <- cs.blockOn(blocker)(IO { server.start() })
      _ <- logger.info("gRPC server started...")
      _ <- logger.info("Registering gRPC server in zookeeper...")
      queue <- serverSettings.queue
      quarantined <- serverSettings.quarantined
      // Create an object that stores whether or not the server is quarantined.
      _ <- registerInZookeeper(serverSettings.discoveryPath, serverSettings.curatorFramework, host)
      loadWriteFiber <- streamLoads(
        queue,
        host,
        serverSettings.discoveryPath,
        serverSettings.curatorFramework,
        serverSettings,
        quarantined
      ).start

    } yield new WookieeGrpcServer(
      server,
      serverSettings.curatorFramework,
      loadWriteFiber,
      queue,
      host,
      serverSettings.discoveryPath,
      quarantined
    )
  }

  private def streamLoads(
      queue: Queue[IO, Int],
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework,
      serverSettings: ServerSettings,
      quarantined: Ref[IO, Boolean]
  )(implicit timer: Timer[IO], cs: ContextShift[IO], blocker: Blocker, logger: Logger[IO]): IO[Unit] = {
    val stream: Stream[IO, Int] = queue.dequeue
    stream
      .debounce(serverSettings.loadUpdateInterval)
      .evalTap { load: Int =>
        for {
          isQuarantined <- quarantined.get
          _ <- if (isQuarantined) {
            logger
              .info(s"In quarantine. Not updating load...")
          } else {
            assignLoad(load, host, discoveryPath, curatorFramework)
              .*>(
                logger
                  .info(s"Wrote load to zookeeper: load = $load")
              )
          }
        } yield ()
      }
      .compile
      .drain
  }

  private def assignLoad(
      load: Int,
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework
  )(implicit cs: ContextShift[IO], blocker: Blocker): IO[Unit] = {
    cs.blockOn(blocker) {
      IO {
        val newHost = Host(host.version, host.address, host.port, HostMetadata(load, host.metadata.quarantined))
        curatorFramework
          .setData()
          .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
        ()
      }
    }
  }

  private def assignQuarantine(
      isQuarantined: Boolean,
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework
  )(implicit cs: ContextShift[IO], blocker: Blocker): IO[Unit] = {
    cs.blockOn(blocker) {
      IO {
        val newHost = Host(host.version, host.address, host.port, HostMetadata(host.metadata.load, isQuarantined))
        curatorFramework
          .setData()
          .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
        ()
      }
    }
  }

  private def registerInZookeeper(
      discoveryPath: String,
      curator: CuratorFramework,
      host: Host
  )(implicit cs: ContextShift[IO], blocker: Blocker): IO[Unit] = {
    cs.blockOn(blocker)(
      IO {
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
    )
  }
}
