package com.oracle.infy.wookiee.grpc.settings

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
    serverServiceDefinition: ServerServiceDefinition,
    host: IO[Host],
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
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int,
      curatorFramework: CuratorFramework
  )(implicit cs: ContextShift[IO]): ServerSettings = {
    ServerSettings(
      discoveryPath,
      serverServiceDefinition,
      IO(host),
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
  }

  def apply(
      discoveryPath: String,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int,
      curatorFramework: CuratorFramework
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
      serverServiceDefinition = serverServiceDefinition,
      host = host,
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
