package com.oracle.infy.wookiee.grpc.impl

import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

private[wookiee] object GRPCUtils {

  protected[wookiee] def eventLoopGroup(dispatcherEC: ExecutionContext, dispatcherECThreads: Int): NioEventLoopGroup =
    new NioEventLoopGroup(dispatcherECThreads, scalaToJavaExecutor(dispatcherEC))

  protected[wookiee] def scalaToJavaExecutor(executor: ExecutionContext): Executor =
    (command: Runnable) => executor.execute(command)

  protected[wookiee] def curatorFramework(
      zookeeperQuorum: String,
      zookeeperBlockingExecutionContext: ExecutionContext,
      retryPolicy: RetryPolicy
  ): CuratorFramework =
    CuratorFrameworkFactory
      .builder()
      .runSafeService(scalaToJavaExecutor(zookeeperBlockingExecutionContext))
      .connectString(zookeeperQuorum)
      .retryPolicy(retryPolicy)
      .build()
}
