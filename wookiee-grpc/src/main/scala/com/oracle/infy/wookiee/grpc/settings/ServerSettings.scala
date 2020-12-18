package com.oracle.infy.wookiee.grpc.settings

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ContextShift, IO}
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import fs2.concurrent.Queue
import io.grpc.ServerServiceDefinition

import java.net.InetAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

final case class ServerSettings(
    zookeeperQuorum: String,
    discoveryPath: String,
    zookeeperRetryInterval: FiniteDuration = 3.seconds,
    zookeeperMaxRetries: Int = 20,
    serverServiceDefinition: ServerServiceDefinition,
    port: Int,
    host: IO[Host],
    bossExecutionContext: ExecutionContext,
    workerExecutionContext: ExecutionContext,
    applicationExecutionContext: ExecutionContext,
    zookeeperBlockingExecutionContext: ExecutionContext,
    timerExecutionContext: ExecutionContext,
    bossThreads: Int,
    workerThreads: Int,
    queue: IO[Queue[IO, Int]],
    quarantined: IO[Ref[IO, Boolean]]
)

object ServerSettings {

  def generateDefaultQueue(
      bossExecutionContext: ExecutionContext
  ): IO[Queue[IO, Int]] = {
    implicit val c: ContextShift[IO] = IO.contextShift(bossExecutionContext)
    Queue.unbounded[IO, Int]
  }

  def apply(
      zookeeperQuorum: String,
      discoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      host: IO[Host],
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      zookeeperBlockingExecutionContext: ExecutionContext,
      timerExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int
  ): ServerSettings = {
    val queue = generateDefaultQueue(bossExecutionContext)
    ServerSettings(
      zookeeperQuorum,
      discoveryPath,
      zookeeperRetryInterval,
      zookeeperMaxRetries,
      serverServiceDefinition,
      port,
      host,
      bossExecutionContext,
      workerExecutionContext,
      applicationExecutionContext,
      zookeeperBlockingExecutionContext,
      timerExecutionContext,
      bossThreads,
      workerThreads,
      queue,
      Ref.of[IO, Boolean](false)
    )
  }

  def apply(
      zookeeperQuorum: String,
      discoveryPath: String,
      serverServiceDefinition: ServerServiceDefinition,
      port: Int,
      timerExecutionContext: ExecutionContext,
      bossExecutionContext: ExecutionContext,
      workerExecutionContext: ExecutionContext,
      applicationExecutionContext: ExecutionContext,
      zookeeperBlockingExecutionContext: ExecutionContext,
      bossThreads: Int,
      workerThreads: Int
  )(implicit cs: ContextShift[IO], blocker: Blocker): ServerSettings = {
    val queue = generateDefaultQueue(bossExecutionContext)
    val host = {
      for {
        address <- cs.blockOn(blocker)(IO {
          InetAddress.getLocalHost.getCanonicalHostName
        })
        host = Host(0, address, port, HostMetadata(0, quarantined = false))
      } yield host
    }
    ServerSettings(
      zookeeperQuorum,
      discoveryPath,
      3.seconds,
      20,
      serverServiceDefinition,
      port,
      host,
      bossExecutionContext,
      workerExecutionContext,
      applicationExecutionContext,
      zookeeperBlockingExecutionContext,
      timerExecutionContext,
      bossThreads,
      workerThreads,
      queue,
      Ref.of[IO, Boolean](false)
    )
  }
}
