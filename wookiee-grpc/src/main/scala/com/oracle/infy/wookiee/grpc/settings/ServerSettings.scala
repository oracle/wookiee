package com.oracle.infy.wookiee.grpc.settings

import cats.data.NonEmptyList
import cats.effect.std.Queue
import cats.effect.{IO, Ref}
import com.oracle.infy.wookiee.grpc.WookieeGrpcUtils.DEFAULT_MAX_MESSAGE_SIZE
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import io.grpc.{ServerInterceptor, ServerServiceDefinition}
import org.apache.curator.framework.CuratorFramework

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

final case class ServerSettings(
    discoveryPath: String,
    serverServiceDefinitions: NonEmptyList[
      (ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])
    ],
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
) {
  // Defaults to 4MB or 4194304
  private val maxMessageSizeRef: AtomicReference[Int] = new AtomicReference[Int](DEFAULT_MAX_MESSAGE_SIZE)

  def withMaxMessageSize(bytes: Int): ServerSettings = {
    maxMessageSizeRef.set(bytes)
    this
  }

  def maxMessageSize(): Int =
    maxMessageSizeRef.get()
}

object ServerSettings {

  def apply(
      discoveryPath: String,
      serverServiceDefinition: ServerServiceDefinition,
      serverInterceptors: Option[List[ServerInterceptor]],
      host: Host,
      sslServerSettings: Option[SSLServerSettings],
      authSettings: Option[ServiceAuthSettings],
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int,
      curatorFramework: CuratorFramework
  ): ServerSettings =
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
      (serverServiceDefinition, authSettings, serverInterceptors)
    )

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
  ): ServerSettings =
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
  ): ServerSettings =
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
  ): ServerSettings =
    ServerSettings(
      discoveryPath,
      NonEmptyList(
        (serverServiceDefinition._1, serverServiceDefinition._2, None),
        otherServiceDefinitions.map(srv => (srv._1, srv._2, None)).toList
      ),
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
  ): ServerSettings = {
    apply(
      discoveryPath = discoveryPath,
      port = port,
      sslServerSettings = sslServerSettings,
      bossExecutionContext = bossExecutionContext,
      workerExecutionContext = workerExecutionContext,
      applicationExecutionContext = applicationExecutionContext,
      bossThreads = bossThreads,
      workerThreads = workerThreads,
      curatorFramework = curatorFramework,
      serverServiceDefinition = (serverServiceDefinition._1, serverServiceDefinition._2, None),
      otherServiceDefinitions = otherServiceDefinitions.map(srv => (srv._1, srv._2, None)).toList: _*
    )
  }

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
      serverServiceDefinition: (ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]]),
      otherServiceDefinitions: (ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])*
  ): ServerSettings =
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
      serverServiceDefinition: (ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]]),
      otherServiceDefinitions: (ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])*
  ): ServerSettings = {
    val host = {
      for {
        address <- IO.blocking {
          InetAddress.getLocalHost.getCanonicalHostName
        }
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
