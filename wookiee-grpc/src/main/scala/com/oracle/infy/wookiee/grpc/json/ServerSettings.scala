package com.oracle.infy.wookiee.grpc.json

import java.net.InetAddress

import cats.effect.{Blocker, ContextShift, IO}
import com.oracle.infy.wookiee.model.Host
import fs2.concurrent.Queue
import io.grpc.ServerServiceDefinition

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

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
      workerThreads: Int,
      queue: IO[Queue[IO, Int]]
  ): ServerSettings = {
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
      zookeeperRetryInterval: FiniteDuration = 3.seconds,
      zookeeperMaxRetries: Int = 20,
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
        val address = c.blockOn(blocker)(IO {
          InetAddress.getLocalHost.getCanonicalHostName
        }).unsafeRunSync() //TODO, see if there's a way around this
        ServerSettings(
          zookeeperQuorum,
          discoveryPath,
          zookeeperRetryInterval,
          zookeeperMaxRetries,
          serverServiceDefinition,
          port,
          Host(0, address, port, Map.empty),
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
