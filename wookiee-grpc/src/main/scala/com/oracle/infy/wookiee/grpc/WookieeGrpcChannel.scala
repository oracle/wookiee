package com.oracle.infy.wookiee.grpc

import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Fiber, IO}
import com.oracle.infy.wookiee.grpc.contract.{HostnameServiceContract, ListenerContract}
import com.oracle.infy.wookiee.grpc.errors.Errors.WookieeGrpcError
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.impl.{BearerTokenClientProvider, Fs2CloseableImpl, WookieeNameResolver, ZookeeperHostnameService}
import com.oracle.infy.wookiee.grpc.settings.{ChannelSettings, ClientAuthSettings, SSLClientSettings}
import com.oracle.infy.wookiee.model.LoadBalancers.{RoundRobinPolicy, LoadBalancingPolicy => LBPolicy}
import com.oracle.infy.wookiee.model.{Host, LoadBalancers}
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.grpc._
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NegotiationType, NettyChannelBuilder}
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext
import org.apache.curator.framework.recipes.cache.CuratorCache

import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

final class WookieeGrpcChannel(val managedChannel: ManagedChannel)(
  implicit cs: ContextShift[IO],
  blocker: Blocker
) {

  def shutdown(): IO[Unit] = {
    cs.blockOn(blocker)(IO({
      managedChannel.shutdown()
      ()
    }))
  }

}

object WookieeGrpcChannel {

  def of(
          settings: ChannelSettings
        )(
          implicit cs: ContextShift[IO],
          concurrent: ConcurrentEffect[IO],
          blocker: Blocker,
          logger: Logger[IO]
        ): IO[WookieeGrpcChannel] = {

    for {
      listener <- Ref.of[IO, Option[ListenerContract[IO, Stream]]](None)
      fiberRef <- Ref.of[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]](None)

      hostnameServiceSemaphore <- Semaphore(1)
      nameResolverSemaphore <- Semaphore(1)
      queue <- Queue.unbounded[IO, Set[Host]]
      killSwitch <- Deferred[IO, Either[Throwable, Unit]]
      cache <- Ref.of[IO, Option[CuratorCache]](None)
      _ <- addLoadBalancer(settings.lbPolicy)
      channel <- cs.blockOn(blocker)(
        buildChannel(
          settings.serviceDiscoveryPath,
          settings.eventLoopGroupExecutionContext,
          settings.channelExecutionContext,
          settings.offloadExecutionContext,
          settings.eventLoopGroupExecutionContextThreads,
          settings.lbPolicy,
          listener,
          nameResolverSemaphore,
          fiberRef,
          new ZookeeperHostnameService(
            settings.curatorFramework,
            cache,
            hostnameServiceSemaphore,
            Fs2CloseableImpl(queue.dequeue, killSwitch),
            queue.enqueue1
          ),
          settings.serviceDiscoveryPath,
          settings.sslClientSettings,
          settings.clientAuthSettings
        )
      )
    } yield new WookieeGrpcChannel(channel)
  }

  private def addLoadBalancer(lbPolicy: LBPolicy): IO[Unit] = IO {
    lbPolicy match {
      case RoundRobinPolicy => ()
      case LoadBalancers.RoundRobinWeightedPolicy =>
        LoadBalancerRegistry
          .getDefaultRegistry
          .register(new LoadBalancerProvider {
            override def isAvailable: Boolean = true

            override def getPriority: Int = 5

            override def getPolicyName: String = "round_robin_weighted"

            override def newLoadBalancer(helper: LoadBalancer.Helper) =
              new RoundRobinWeightedLoadBalancer(helper)
          })
    }
  }

  private def buildChannel(
                            path: String,
                            eventLoopGroupExecutionContext: ExecutionContext,
                            channelExecutionContext: ExecutionContext,
                            offloadExecutionContext: ExecutionContext,
                            eventLoopGroupExecutionContextThreads: Int,
                            lbPolicy: LBPolicy,
                            listenerRef: Ref[IO, Option[ListenerContract[IO, Stream]]],
                            semaphore: Semaphore[IO],
                            fiberRef: Ref[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]],
                            hostnameServiceContract: HostnameServiceContract[IO, Stream],
                            discoveryPath: String,
                            sslClientSettings: Option[SSLClientSettings],
                            authClientSettings: Option[ClientAuthSettings]
                          )(implicit cs: ContextShift[IO], blocker: Blocker, logger: Logger[IO]): IO[ManagedChannel] = IO {
    val channelExecutorJava = scalaToJavaExecutor(channelExecutionContext)
    val offloadExecutorJava = scalaToJavaExecutor(offloadExecutionContext)

    val builder = NettyChannelBuilder
      .forTarget(s"zookeeper://$path")
      .idleTimeout(Long.MaxValue, TimeUnit.DAYS)
      .nameResolverFactory(
        new NameResolver.Factory {
          override def newNameResolver(targetUri: URI, args: NameResolver.Args): NameResolver = {
            new WookieeNameResolver(listenerRef, semaphore, fiberRef, hostnameServiceContract, discoveryPath)
          }

          override def getDefaultScheme: String = "zookeeper"
        }
      )
      .defaultLoadBalancingPolicy(lbPolicy match {
        case RoundRobinPolicy => "round_robin"
        case LoadBalancers.RoundRobinWeightedPolicy => "round_robin_weighted"
      })
      .usePlaintext()
      .channelFactory(() => new NioSocketChannel())
      .eventLoopGroup(eventLoopGroup(eventLoopGroupExecutionContext, eventLoopGroupExecutionContextThreads))
      .executor(channelExecutorJava)
      .offloadExecutor(offloadExecutorJava)

    if (sslClientSettings.nonEmpty) {
      logger.info("gRPC client using mTLS to establish connections.")
      builder
        .negotiationType(NegotiationType.TLS)
        .sslContext(buildSslContext(sslClientSettings.get))
    }

    if (authClientSettings.nonEmpty) {
      logger.info("gRPC client using bearer token authentication.")
      builder.intercept(BearerTokenClientProvider(authClientSettings.get))
    }

    builder
      .build()
  }

  private def buildSslContext(sslClientSettings: SSLClientSettings): SslContext = {
    val builder = GrpcSslContexts.forClient

    if (sslClientSettings.sslCertificateTrustPath.nonEmpty) builder.trustManager(new File(sslClientSettings.sslCertificateTrustPath))

    if (sslClientSettings.sslCertificateChainPath.nonEmpty && sslClientSettings.sslPrivateKeyPath.nonEmpty && sslClientSettings.sslPassphrase.nonEmpty) {
      builder.keyManager(new File(sslClientSettings.sslCertificateChainPath), new File(sslClientSettings.sslPrivateKeyPath), sslClientSettings.sslPassphrase.get)
    } else if (sslClientSettings.sslCertificateChainPath.nonEmpty && sslClientSettings.sslPrivateKeyPath.nonEmpty) {
      builder.keyManager(new File(sslClientSettings.sslCertificateChainPath), new File(sslClientSettings.sslPrivateKeyPath))
    }
    builder.build
  }
}
