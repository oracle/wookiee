package com.oracle.infy.wookiee.component.grpc.server

import cats.data.NonEmptyList
import cats.effect.{Blocker, ContextShift, IO, Timer}
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.oracle.infy.wookiee.grpc.WookieeGrpcServer
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings.{SSLServerSettings, ServerSettings, ServiceAuthSettings}
import com.typesafe.config.Config
import io.grpc.{ServerInterceptor, ServerServiceDefinition}
import org.typelevel.log4cats.Logger

import scala.concurrent.ExecutionContext
import scala.util.Try

trait GrpcServer extends ExtensionHostServices {

  def startGrpcServers(
      services: NonEmptyList[(ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])],
      config: Config
  )(
      implicit
      ec: ExecutionContext,
      blocker: Blocker,
      logger: Logger[IO],
      timer: Timer[IO],
      cs: ContextShift[IO]
  ): IO[WookieeGrpcServer] = {
    val sslSettings: Option[SSLServerSettings] =
      Try(config.getString(s"${GrpcManager.ComponentName}.grpc.ssl.cert-chain-path"))
        .toOption
        .map({ certPath =>
          SSLServerSettings(
            certPath,
            config.getString(s"${GrpcManager.ComponentName}.grpc.ssl.private-key-path"),
            Try(config.getString(s"${GrpcManager.ComponentName}.grpc.ssl.passphrase")).toOption,
            Try(config.getString(s"${GrpcManager.ComponentName}.grpc.ssl.cert-trust-path")).toOption
          )
        })

    val zkPath = Try(config.getString(s"${GrpcManager.ComponentName}.grpc.zk-discovery-path"))
      .recover { _ =>
        throw new IllegalArgumentException(
          s"Need to set config field [${GrpcManager.ComponentName}.grpc.zk-discovery-path]"
        )
      }
      .getOrElse("")
    val port = config.getInt(s"${GrpcManager.ComponentName}.grpc.port")

    val serverSettings = ServerSettings(
      discoveryPath = zkPath,
      port = port,
      sslServerSettings = sslSettings,
      bossExecutionContext = createEC(s"${getClass.getSimpleName}-grpc-boss"),
      workerExecutionContext = createEC(s"${getClass.getSimpleName}-grpc-worker"),
      applicationExecutionContext = ec,
      bossThreads = bossThreads,
      workerThreads = workerThreads,
      curatorFramework = getCurator,
      serverServiceDefinition = services.head,
      services.toList.drop(1): _*
    )

    val hostName = Try(config.getString(s"${GrpcManager.ComponentName}.grpc.server-host-name"))
    val finalServerSettings = hostName
      .map(
        hostName =>
          serverSettings.copy(
            host = IO(Host(0, hostName, port, HostMetadata(0, quarantined = false)))
          )
      )
      .getOrElse(serverSettings)

    log.info(
      s"Starting gRPC Services: Host = [${hostName.getOrElse("localhost")}:" +
        s"$port], ZK Path = [$zkPath]"
    )

    WookieeGrpcServer.start(finalServerSettings)
  }
}
