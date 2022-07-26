package com.oracle.infy.wookiee.grpc.settings

import com.oracle.infy.wookiee.grpc.WookieeGrpcUtils.DEFAULT_MAX_MESSAGE_SIZE
import com.oracle.infy.wookiee.grpc.model.LoadBalancers.LoadBalancingPolicy
import io.grpc.ClientInterceptor
import org.apache.curator.framework.CuratorFramework

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext

final case class ChannelSettings(
    serviceDiscoveryPath: String,
    eventLoopGroupExecutionContext: ExecutionContext,
    channelExecutionContext: ExecutionContext,
    offloadExecutionContext: ExecutionContext,
    eventLoopGroupExecutionContextThreads: Int,
    lbPolicy: LoadBalancingPolicy,
    curatorFramework: CuratorFramework,
    sslClientSettings: Option[SSLClientSettings],
    clientAuthSettings: Option[ClientAuthSettings],
    clientInterceptors: Option[List[ClientInterceptor]]
) {
  // Defaults to 4MB or 4194304
  private val maxMessageSizeRef: AtomicReference[Int] = new AtomicReference[Int](DEFAULT_MAX_MESSAGE_SIZE)

  def withMaxMessageSize(bytes: Int): ChannelSettings = {
    maxMessageSizeRef.set(bytes)
    this
  }

  def maxMessageSize(): Int =
    maxMessageSizeRef.get()
}

object ChannelSettings {

  def apply(
      serviceDiscoveryPath: String,
      eventLoopGroupExecutionContext: ExecutionContext,
      channelExecutionContext: ExecutionContext,
      offloadExecutionContext: ExecutionContext,
      eventLoopGroupExecutionContextThreads: Int,
      lbPolicy: LoadBalancingPolicy,
      curatorFramework: CuratorFramework,
      sslClientSettings: Option[SSLClientSettings],
      clientAuthSettings: Option[ClientAuthSettings]
  ): ChannelSettings = ChannelSettings(
    serviceDiscoveryPath,
    eventLoopGroupExecutionContext,
    channelExecutionContext,
    offloadExecutionContext,
    eventLoopGroupExecutionContextThreads,
    lbPolicy,
    curatorFramework,
    sslClientSettings,
    clientAuthSettings,
    None
  )
}
