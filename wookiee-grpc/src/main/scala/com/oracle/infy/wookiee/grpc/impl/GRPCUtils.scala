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

  protected[grpc] def scalaToJavaExecutor(executor: ExecutionContext): Executor = new java.util.concurrent.Executor {
    override def execute(command: Runnable): Unit = executor.execute(command)
  }

  protected[grpc] def curatorFramework(
      zookeeperQuorum: String,
      blockingExecutionContext: ExecutionContext,
      retryPolicy: ExponentialBackoffRetry
  ): CuratorFramework = {
    CuratorFrameworkFactory
      .builder()
      .runSafeService(scalaToJavaExecutor(blockingExecutionContext))
      .connectString(zookeeperQuorum)
      .retryPolicy(retryPolicy)
      .build()
  }
}
