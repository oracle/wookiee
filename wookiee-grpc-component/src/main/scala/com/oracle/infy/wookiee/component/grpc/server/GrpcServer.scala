package com.oracle.infy.wookiee.component.grpc.server

import cats.data.NonEmptyList
import cats.effect.IO
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.oracle.infy.wookiee.grpc.WookieeGrpcServer
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings.{SSLServerSettings, ServerSettings, ServiceAuthSettings}
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config
import io.grpc.{ServerInterceptor, ServerServiceDefinition}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.util.Try

trait GrpcServer extends ExtensionHostServices {

  def startGrpcServers(
      services: NonEmptyList[(ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])],
      config: Config
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
      .recover {
        case _: Throwable =>
          throw new IllegalArgumentException( //scalafix:ok
            s"Need to set config field [${GrpcManager.ComponentName}.grpc.zk-discovery-path]"
          )
      }
      .getOrElse("")
    val port = config.getInt(s"${GrpcManager.ComponentName}.grpc.port")
    val maxMessageSize = config.getInt(s"${GrpcManager.ComponentName}.grpc.max-message-size")

    val serverSettings = ServerSettings(
      discoveryPath = zkPath,
      port = port,
      sslServerSettings = sslSettings,
      bossExecutionContext = ThreadUtil.createEC(s"${getClass.getSimpleName}-grpc-boss"),
      workerExecutionContext = ThreadUtil.createEC(s"${getClass.getSimpleName}-grpc-worker"),
      applicationExecutionContext = ThreadUtil.createEC(s"${getClass.getSimpleName}-grpc-application"),
      bossThreads = bossThreads,
      workerThreads = workerThreads,
      curatorFramework = getCurator,
      serverServiceDefinition = services.head,
      services.toList.drop(1): _*
    ).withMaxMessageSize(maxMessageSize)

    val hostName = Try(config.getString(s"${GrpcManager.ComponentName}.grpc.server-host-name"))
    val finalServerSettings = hostName
      .map(
        hName =>
          serverSettings
            .copy(
              host = IO(Host(0, hName, port, HostMetadata(0, quarantined = false)))
            )
            .withMaxMessageSize(serverSettings.maxMessageSize())
      )
      .getOrElse(serverSettings)

    log.info(
      s"Starting gRPC Services: Host = [${hostName.getOrElse("localhost")}:" +
        s"$port], ZK Path = [$zkPath]"
    )

    for {
      implicit0(logger: Logger[IO]) <- Slf4jLogger
        .create[IO]
        .map(l => l: Logger[IO])

      server <- WookieeGrpcServer.start(finalServerSettings)
    } yield server
  }
}
