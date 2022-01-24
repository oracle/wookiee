package com.oracle.infy.wookiee.grpc.settings

import cats.data.NonEmptyList
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ContextShift, IO}
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import fs2.concurrent.Queue
import io.grpc.ServerServiceDefinition
import org.apache.curator.framework.CuratorFramework

import java.net.InetAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

final case class ServerSettings(
    discoveryPath: String,
    serverServiceDefinitions: NonEmptyList[(ServerServiceDefinition, Option[ServiceAuthSettings])],
    host: IO[Host],
    sslServerSettings: Option[SSLServerSettings],
    bossExecutionContext: ExecutionContext,
    workerExecutionContext: ExecutionContext,
    applicationExecutionContext: ExecutionContext,
    bossThreads: Int,
    workerThreads: Int,
    loadUpdateInterval: FiniteDuration,
    queue: IO[Queue[IO, Int]],
    quarantined: IO[Ref[IO, Boolean]],
    curatorFramework: CuratorFramework
)

object ServerSettings {

  def apply(
      discoveryPath: String,
      serverServiceDefinition: ServerServiceDefinition,
      host: Host,
      sslServerSettings: Option[SSLServerSettings],
      authSettings: Option[ServiceAuthSettings],
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int,
      curatorFramework: CuratorFramework
  )(implicit cs: ContextShift[IO]): ServerSettings =
    apply(
      discoveryPath,
      host,
      sslServerSettings,
      bossExecutionContext,
      workerExecutionContext,
      applicationExecutionContext,
      bossThreads,
      workerThreads,
      curatorFramework,
      (serverServiceDefinition, authSettings)
    )

  def apply(
      discoveryPath: String,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      sslServerSettings: Option[SSLServerSettings],
      authSettings: Option[ServiceAuthSettings],
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int,
      curatorFramework: CuratorFramework
  )(implicit cs: ContextShift[IO], blocker: Blocker): ServerSettings =
    apply(
      discoveryPath,
      port,
      sslServerSettings,
      bossExecutionContext,
      workerExecutionContext,
      applicationExecutionContext,
      bossThreads,
      workerThreads,
      curatorFramework,
      (serverServiceDefinition, authSettings)
    )

  // Use when you'd like to register more than one class to this host and discoveryPath
  def apply(
      discoveryPath: String,
      host: Host,
      sslServerSettings: Option[SSLServerSettings],
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int,
      curatorFramework: CuratorFramework,
      serverServiceDefinition: (ServerServiceDefinition, Option[ServiceAuthSettings]),
      otherServiceDefinitions: (ServerServiceDefinition, Option[ServiceAuthSettings])*
  )(implicit cs: ContextShift[IO]): ServerSettings =
    ServerSettings(
      discoveryPath,
      NonEmptyList(serverServiceDefinition, otherServiceDefinitions.toList),
      IO(host),
      sslServerSettings,
      bossExecutionContext,
      workerExecutionContext,
      applicationExecutionContext,
      bossThreads,
      workerThreads,
      1.minute,
      Queue.unbounded[IO, Int],
      Ref.of[IO, Boolean](false),
      curatorFramework
    )

  def apply(
      discoveryPath: String,
      port: Int,
      sslServerSettings: Option[SSLServerSettings],
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int,
      curatorFramework: CuratorFramework,
      serverServiceDefinition: (ServerServiceDefinition, Option[ServiceAuthSettings]),
      otherServiceDefinitions: (ServerServiceDefinition, Option[ServiceAuthSettings])*
  )(implicit cs: ContextShift[IO], blocker: Blocker): ServerSettings = {
    val host = {
      for {
        address <- cs.blockOn(blocker)(IO {
          InetAddress.getLocalHost.getCanonicalHostName
        })
        host = Host(0, address, port, HostMetadata(0, quarantined = false))
      } yield host
    }
    ServerSettings(
      discoveryPath = discoveryPath,
      serverServiceDefinitions = NonEmptyList(serverServiceDefinition, otherServiceDefinitions.toList),
      host = host,
      sslServerSettings = sslServerSettings,
      bossExecutionContext = bossExecutionContext,
      workerExecutionContext = workerExecutionContext,
      applicationExecutionContext = applicationExecutionContext,
      bossThreads = bossThreads,
      workerThreads = workerThreads,
      loadUpdateInterval = 1.minute,
      queue = Queue.unbounded[IO, Int],
      quarantined = Ref.of[IO, Boolean](false),
      curatorFramework = curatorFramework
    )
  }
}
