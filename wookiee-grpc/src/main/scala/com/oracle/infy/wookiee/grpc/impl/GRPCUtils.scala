package com.oracle.infy.wookiee.grpc.impl

import java.util.concurrent.Executor

import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

private[grpc] object GRPCUtils {

  protected[grpc] def eventLoopGroup(dispatcherEC: ExecutionContext, dispatcherECThreads: Int): NioEventLoopGroup = {
    new NioEventLoopGroup(dispatcherECThreads, scalaToJavaExecutor(dispatcherEC))
  }

  protected[grpc] def exponentialBackoffRetry(
      baseSleepTime: FiniteDuration,
      maxRetries: Int
  ): ExponentialBackoffRetry = {
    new ExponentialBackoffRetry(baseSleepTime.toMillis.toInt, maxRetries)
  }

  protected[grpc] def scalaToJavaExecutor(executor: ExecutionContext): Executor =
    (command: Runnable) => executor.execute(command)

  protected[grpc] def curatorFramework(
      zookeeperQuorum: String,
      zookeeperBlockingExecutionContext: ExecutionContext,
      retryPolicy: ExponentialBackoffRetry
  ): CuratorFramework = {
    val _ = zookeeperBlockingExecutionContext
    CuratorFrameworkFactory
      .builder()
      .connectString(zookeeperQuorum)
      .retryPolicy(retryPolicy)
      .build()
  }
}
