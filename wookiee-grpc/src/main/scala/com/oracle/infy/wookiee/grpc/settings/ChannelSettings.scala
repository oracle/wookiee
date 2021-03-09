package com.oracle.infy.wookiee.grpc.settings

import com.oracle.infy.wookiee.model.LoadBalancers.LoadBalancingPolicy
import org.apache.curator.framework.CuratorFramework

import scala.concurrent.ExecutionContext

final case class ChannelSettings(
    serviceDiscoveryPath: String,
    eventLoopGroupExecutionContext: ExecutionContext,
    channelExecutionContext: ExecutionContext,
    offloadExecutionContext: ExecutionContext,
    eventLoopGroupExecutionContextThreads: Int,
    lbPolicy: LoadBalancingPolicy,
    curatorFramework: CuratorFramework,
    sslClientSettings: Option[SSLClientSettings] = None,
    clientAuthSettings: Option[ClientAuthSettings] = None
)

final case class SSLClientSettings(
    sslCertificateChainPath: String,
    sslPrivateKeyPath: String,
    sslPassphrase: Option[String],
    sslCertificateTrustPath: String
)

final case class ClientAuthSettings(
    token: String
)