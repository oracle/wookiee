package com.oracle.infy.wookiee.grpc.json

import java.net.InetAddress

import cats.effect.{Blocker, ContextShift, IO}
import com.oracle.infy.wookiee.model.Host
import fs2.concurrent.Queue
import io.grpc.ServerServiceDefinition

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

case class ServerSettings(
    zookeeperQuorum: String,
    discoveryPath: String,
    zookeeperRetryInterval: FiniteDuration = 3.seconds,
    zookeeperMaxRetries: Int = 20,
    serverServiceDefinition: ServerServiceDefinition,
    port: Int,
    host: Host,
    bossExecutionContext: ExecutionContext,
    workerExecutionContext: ExecutionContext,
    applicationExecutionContext: ExecutionContext,
    zookeeperBlockingExecutionContext: ExecutionContext,
    timerExecutionContext: ExecutionContext,
    bossThreads: Int,
    workerThreads: Int,
    queue: IO[Queue[IO, Int]]
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
      host: Host,
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
      queue
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
  ): ServerSettings = {
    implicit val c: ContextShift[IO] = IO.contextShift(bossExecutionContext)
    implicit val blocker = Blocker.liftExecutionContext(zookeeperBlockingExecutionContext)
    val queue = generateDefaultQueue(bossExecutionContext)
    val host = {
      for {
        address <- c.blockOn(blocker)(IO {
          InetAddress.getLocalHost.getCanonicalHostName
        })
        host = Host(0, address, port, Map.empty)
      } yield host
    }
    ServerSettings(
      zookeeperQuorum,
      discoveryPath,
      3.seconds,
      20,
      serverServiceDefinition,
      port,
      host.unsafeRunSync(),
      bossExecutionContext,
      workerExecutionContext,
      applicationExecutionContext,
      zookeeperBlockingExecutionContext,
      timerExecutionContext,
      bossThreads,
      workerThreads,
      queue
    )
  }
}
