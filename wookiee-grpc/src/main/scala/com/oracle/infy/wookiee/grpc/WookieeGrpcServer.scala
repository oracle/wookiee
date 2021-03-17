package com.oracle.infy.wookiee.grpc

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ContextShift, Fiber, IO, Timer}
import com.oracle.infy.wookiee.grpc.impl.BearerTokenAuthenticator
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.grpc.settings.{SSLServerSettings, ServerSettings}
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NettyServerBuilder}
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel
import io.grpc.netty.shaded.io.netty.handler.ssl.{ClientAuth, SslContext, SslContextBuilder, SslProvider}
import io.grpc.{Server, ServerInterceptors}
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode

import java.io.File
import scala.util.Try

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
      server <- cs.blockOn(blocker)(buildServer(serverSettings, host))
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

  private def buildServer(serverSettings: ServerSettings, host: Host)(implicit logger: Logger[IO]): IO[Server] = {
    for {
      _ <- logger.info("Building gRPC server...")
      builder0 = NettyServerBuilder
        .forPort(host.port)
        .channelFactory(() => new NioServerSocketChannel())

      builder1 <- serverSettings
        .sslServerSettings
        .map(getSslContextBuilder)
        .map(_.map(sslCtx => builder0.sslContext(sslCtx)))
        .getOrElse(IO(builder0))

      builder2 = IO {
        builder1
          .bossEventLoopGroup(eventLoopGroup(serverSettings.bossExecutionContext, serverSettings.bossThreads))
          .workerEventLoopGroup(eventLoopGroup(serverSettings.workerExecutionContext, serverSettings.workerThreads))
          .executor(scalaToJavaExecutor(serverSettings.applicationExecutionContext))
      }

      builder3 <- serverSettings
        .serverServiceDefinitions
        .foldLeft(builder2) {
          case (builderIO, (serverServiceDefinition, maybeAuth)) =>
            maybeAuth
              .map { authSettings =>
                logger
                  .info(
                    s"Adding gRPC service [${serverServiceDefinition.getServiceDescriptor.getName}] with authentication"
                  )
                  .*>(builderIO)
                  .map { builder =>
                    builder.addService(
                      ServerInterceptors.intercept(serverServiceDefinition, BearerTokenAuthenticator(authSettings))
                    )
                  }
              }
              .getOrElse(
                logger
                  .info(
                    s"Adding gRPC service [${serverServiceDefinition.getServiceDescriptor.getName}] without authentication"
                  )
                  .*>(builderIO)
                  .map { builder =>
                    builder.addService(serverServiceDefinition)
                  }
              )
        }

      _ <- logger.info("Successfully built gRPC server")
      server <- IO { builder3.build() }

    } yield server
  }

  private def getSslContextBuilder(
      sslServerSettings: SSLServerSettings
  )(implicit logger: Logger[IO]): IO[SslContext] = {

    for {

      sslClientContextBuilder0 <- IO {
        sslServerSettings
          .sslPassphrase
          .map { passphrase =>
            SslContextBuilder.forServer(
              new File(sslServerSettings.sslCertificateChainPath),
              new File(sslServerSettings.sslPrivateKeyPath),
              passphrase
            )
          }
          .getOrElse(
            SslContextBuilder.forServer(
              new File(sslServerSettings.sslCertificateChainPath),
              new File(sslServerSettings.sslPrivateKeyPath)
            )
          )
      }

      sslContextBuilder1 <- sslServerSettings
        .sslCertificateTrustPath
        .map { path =>
          logger
            .info("gRPC server will require mTLS for client connections.")
            .as(
              sslClientContextBuilder0
                .trustManager(new File(path))
                .clientAuth(ClientAuth.REQUIRE)
            )
        }
        .getOrElse(
          logger
            .info("gRPC server has TLS enabled.")
            .as(sslClientContextBuilder0)
        )

      sslContext <- IO {
        GrpcSslContexts.configure(sslContextBuilder1, SslProvider.OPENSSL).build()
      }
    } yield sslContext
  }

  private def streamLoads(
      queue: Queue[IO, Int],
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework,
      serverSettings: ServerSettings,
      quarantined: Ref[IO, Boolean]
  )(implicit timer: Timer[IO], cs: ContextShift[IO], blocker: Blocker, logger: Logger[IO]): IO[Unit] = {
    val stream = queue.dequeue
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

        val path = s"$discoveryPath/${host.address}:${host.port}"

        // Remove any nodes attached to old sessions first (if they exists)
        Try {
          curator
            .delete()
            .forPath(path)
        }

        curator
          .create
          .orSetData()
          .withMode(CreateMode.EPHEMERAL)
          .forPath(path, HostSerde.serialize(host))
        ()
      }
    )
  }
}
