package com.oracle.infy.wookiee.component.grpc.server

import cats.data.NonEmptyList
import cats.effect.{Blocker, ContextShift, IO, Timer}
import com.oracle.infy.wookiee.grpc.WookieeGrpcServer
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings.{SSLServerSettings, ServerSettings, ServiceAuthSettings}
import io.grpc.ServerServiceDefinition
import org.typelevel.log4cats.Logger

import scala.concurrent.ExecutionContext

trait GrpcServer extends ExtensionHostServices {

  def startGrpcServers(
                        services: NonEmptyList[(ServerServiceDefinition, Option[ServiceAuthSettings])],
                        cdrConfig: CdrConfig
                      )(implicit
                        ec: ExecutionContext,
                        blocker: Blocker,
                        logger: Logger[IO],
                        timer: Timer[IO],
                        cs: ContextShift[IO]
                      ): IO[WookieeGrpcServer] = {
    val sslSettings = cdrConfig
      .grpcConfig
      .sslConfig
      .map(s => SSLServerSettings(s.certChainPath, s.privateKeyPath, s.passphrase, s.certTrustPath))

    val serverSettings = ServerSettings(
      discoveryPath = cdrConfig.zookeeperConfig.discoveryPath,
      port = cdrConfig.grpcConfig.serverPort,
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

    val finalServerSettings = grpcConfig
      .serverHostName
      .map(hostName =>
        serverSettings.copy(
          host = IO(Host(0, hostName, cdrConfig.grpcConfig.serverPort, HostMetadata(0, quarantined = false)))
        )
      )
      .getOrElse(serverSettings)

    log.info(
      s"Starting gRPC Services: Host = [${cdrConfig.grpcConfig.serverHostName.getOrElse("localhost")}:" +
        s"${cdrConfig.grpcConfig.serverPort}], ZK Path = [${cdrConfig.zookeeperConfig.discoveryPath}]"
    )

    WookieeGrpcServer.start(finalServerSettings)
  }
}
