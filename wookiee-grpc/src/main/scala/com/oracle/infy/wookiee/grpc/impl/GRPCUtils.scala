package com.oracle.infy.wookiee.grpc.impl

import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

private[grpc] object GRPCUtils {

  protected[grpc] def eventLoopGroup(dispatcherEC: ExecutionContext, dispatcherECThreads: Int): NioEventLoopGroup = {
    new NioEventLoopGroup(dispatcherECThreads, scalaToJavaExecutor(dispatcherEC))
  }

  protected[grpc] def scalaToJavaExecutor(executor: ExecutionContext): Executor =
    (command: Runnable) => executor.execute(command)

  protected[grpc] def curatorFramework(
      zookeeperQuorum: String,
      zookeeperBlockingExecutionContext: ExecutionContext,
      retryPolicy: RetryPolicy
  ): CuratorFramework = {
    CuratorFrameworkFactory
      .builder()
      .runSafeService(scalaToJavaExecutor(zookeeperBlockingExecutionContext))
      .connectString(zookeeperQuorum)
      .retryPolicy(retryPolicy)
      .build()
  }
}
