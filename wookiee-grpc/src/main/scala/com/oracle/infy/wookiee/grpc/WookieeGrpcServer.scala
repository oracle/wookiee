package com.oracle.infy.wookiee.grpc

import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.effect.{FiberIO, IO, Ref, Temporal}
import com.oracle.infy.wookiee.grpc.impl.BearerTokenAuthenticator
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils.{eventLoopGroup, _}
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings.{SSLServerSettings, ServerSettings}
import fs2._
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NettyServerBuilder}
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel
import io.grpc.netty.shaded.io.netty.handler.ssl.{ClientAuth, SslContext, SslContextBuilder, SslProvider}
import io.grpc.{Server, ServerInterceptors}
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode
import org.typelevel.log4cats.Logger

import java.io.File
import scala.util.Try

final class WookieeGrpcServer(
    private val server: Server,
    private val curatorFramework: CuratorFramework,
    private val fiber: FiberIO[Unit],
    private val loadQueue: Queue[IO, Int],
    private val host: Host,
    private val discoveryPath: String,
    private val quarantined: Ref[IO, Boolean],
    private val bossEventLoop: EventLoopGroup,
    private val workerEventLoop: EventLoopGroup
)(
    implicit
    logger: Logger[IO]
) extends AutoCloseable {

  def shutdown(): IO[Unit] =
    for {
      _ <- logger.debug("WGS300: Stopping load writing process...")
      _ <- fiber.cancel
      _ <- logger.debug("WGS401: Shutting down gRPC server...")
      _ <- IO.blocking(server.shutdown())
      _ <- IO(server.awaitTermination())
      _ <- IO(bossEventLoop.shutdownGracefully())
      _ <- IO(workerEventLoop.shutdownGracefully())
      _ <- logger.info("WGS402: Shutdown of gRPC server and event loops complete")
    } yield ()

  def assignLoad(load: Int): IO[Unit] =
    loadQueue.offer(load)

  def enterQuarantine(): IO[Unit] =
    quarantined
      .getAndSet(true)
      .*>(
        WookieeGrpcServer.assignQuarantine(isQuarantined = true, host, discoveryPath, curatorFramework)
      )

  def exitQuarantine(): IO[Unit] =
    quarantined
      .getAndSet(false)
      .*>(
        WookieeGrpcServer.assignQuarantine(isQuarantined = false, host, discoveryPath, curatorFramework)
      )

  override def close(): Unit =
    shutdown().unsafeRunSync()
}

object WookieeGrpcServer {

  def start(serverSettings: ServerSettings)(
      implicit
      logger: Logger[IO]
  ): IO[WookieeGrpcServer] =
    for {
      host <- serverSettings.host
      bossEventLoop = eventLoopGroup(serverSettings.bossExecutionContext, serverSettings.bossThreads)
      workerEventLoop = eventLoopGroup(serverSettings.workerExecutionContext, serverSettings.workerThreads)
      server <- buildServer(serverSettings, host, bossEventLoop, workerEventLoop)
      _ <- IO.blocking { server.start() }
      _ <- logger.info("WGS100: gRPC server started...")
      _ <- logger.debug("WGS101: Registering gRPC server in zookeeper...")
      queue <- serverSettings.queue
      quarantined <- serverSettings.quarantined
      // Create an object that stores whether or not the server is quarantined.
      _ <- registerInZookeeper(serverSettings.discoveryPath, serverSettings.curatorFramework, host).recoverWith({
        case e: Exception =>
          // If we fail to register in ZK, shutdown grpc server until we can
          logger.error(e)(s"WGS102: Failed to register in zookeeper").*> {
            IO.blocking {
              server.shutdown()
              throw e // scalafix:ok
            }
          }
      })
      _ <- logger.info("WGS103: ZK registration complete, beginning to receive requests")
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
      quarantined,
      bossEventLoop,
      workerEventLoop
    )

  private def buildServer(
      serverSettings: ServerSettings,
      host: Host,
      bossEventLoop: EventLoopGroup,
      workerEventLoop: EventLoopGroup
  )(implicit logger: Logger[IO]): IO[Server] =
    for {
      _ <- logger.info("WGS90: Building gRPC server...")
      builder0 = NettyServerBuilder
        .forPort(host.port)
        .maxInboundMessageSize(serverSettings.maxMessageSize())
        .channelFactory(() => new NioServerSocketChannel())

      builder1 <- serverSettings
        .sslServerSettings
        .map(getSslContextBuilder)
        .map(_.map(sslCtx => builder0.sslContext(sslCtx)))
        .getOrElse(IO(builder0))

      builder2 = IO {
        builder1
          .bossEventLoopGroup(bossEventLoop)
          .workerEventLoopGroup(workerEventLoop)
          .executor(scalaToJavaExecutor(serverSettings.applicationExecutionContext))
      }

      builder3 <- serverSettings
        .serverServiceDefinitions
        .foldLeft(builder2) {
          case (builderIO, (serverServiceDefinition, maybeAuth, maybeInterceptors)) =>
            maybeAuth
              .map { authSettings =>
                logger
                  .info(
                    s"WGS91: Adding gRPC service [${serverServiceDefinition.getServiceDescriptor.getName}] with authentication"
                  )
                  .*>(builderIO)
                  .map { builder =>
                    val interceptors = List(BearerTokenAuthenticator(authSettings)) ++ maybeInterceptors.getOrElse(
                      List()
                    )
                    builder.addService(
                      ServerInterceptors.intercept(serverServiceDefinition, interceptors: _*)
                    )
                  }
              }
              .getOrElse(
                logger
                  .info(
                    s"WGS92: Adding gRPC service [${serverServiceDefinition.getServiceDescriptor.getName}] without authentication"
                  )
                  .*>(builderIO)
                  .map { builder =>
                    val interceptors = maybeInterceptors.getOrElse(
                      List()
                    )
                    builder.addService(
                      ServerInterceptors.intercept(serverServiceDefinition, interceptors: _*)
                    )
                  }
              )
        }

      _ <- logger.debug("WGS93: Successfully built gRPC server")
      server <- IO { builder3.build() }

    } yield server

  private def getSslContextBuilder(
      sslServerSettings: SSLServerSettings
  )(implicit logger: Logger[IO]): IO[SslContext] =
    for {

      sslClientContextBuilder0 <- IO.blocking {
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
            .info("WGS80: gRPC server will require mTLS for client connections.")
            .as(
              sslClientContextBuilder0
                .trustManager(new File(path))
                .clientAuth(ClientAuth.REQUIRE)
            )
        }
        .getOrElse(
          logger
            .info("WGS81: gRPC server has TLS enabled.")
            .as(sslClientContextBuilder0)
        )

      sslContext <- IO.blocking {
        GrpcSslContexts.configure(sslContextBuilder1, SslProvider.OPENSSL).build()
      }
    } yield sslContext

  private def streamLoads(
      queue: Queue[IO, Int],
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework,
      serverSettings: ServerSettings,
      quarantined: Ref[IO, Boolean]
  )(implicit timer: Temporal[IO], logger: Logger[IO]): IO[Unit] = {
    val stream = Stream.repeatEval(queue.take)
    stream
      .debounce(serverSettings.loadUpdateInterval)
      .evalTap { load: Int =>
        for {
          isQuarantined <- quarantined.get
          _ <- if (isQuarantined) {
            logger
              .info(s"WGS70: In quarantine. Not updating load...")
          } else {
            assignLoad(load, host, discoveryPath, curatorFramework)
              .*>(
                logger
                  .info(s"WGS71: Wrote load to zookeeper: load = $load")
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
  ): IO[Unit] =
    IO.blocking {
      val newHost = Host(host.version, host.address, host.port, HostMetadata(load, host.metadata.quarantined))
      curatorFramework
        .setData()
        .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
      ()
    }

  private def assignQuarantine(
      isQuarantined: Boolean,
      host: Host,
      discoveryPath: String,
      curatorFramework: CuratorFramework
  ): IO[Unit] =
    IO.blocking {
      val newHost = Host(host.version, host.address, host.port, HostMetadata(host.metadata.load, isQuarantined))
      curatorFramework
        .setData()
        .forPath(s"$discoveryPath/${host.address}:${host.port}", HostSerde.serialize(newHost))
      ()
    }

  private def registerInZookeeper(
      discoveryPath: String,
      curator: CuratorFramework,
      host: Host
  ): IO[Unit] =
    IO.blocking {
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
}
