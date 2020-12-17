package com.oracle.infy.wookiee.grpc.settings

import com.oracle.infy.wookiee.model.LoadBalancers.LoadBalancingPolicy

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

final case class ChannelSettings(
    zookeeperQuorum: String,
    serviceDiscoveryPath: String,
    zookeeperRetryInterval: FiniteDuration,
    zookeeperMaxRetries: Int,
    zookeeperBlockingExecutionContext: ExecutionContext,
    eventLoopGroupExecutionContext: ExecutionContext,
    channelExecutionContext: ExecutionContext,
    offloadExecutionContext: ExecutionContext,
    eventLoopGroupExecutionContextThreads: Int,
    lbPolicy: LoadBalancingPolicy
)
