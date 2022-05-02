package com.oracle.infy.wookiee.grpc

import cats.effect.std.{Queue, Semaphore}
import cats.effect.{Deferred, FiberIO, IO, Ref, unsafe}
import cats.implicits.catsSyntaxApplicativeId
import com.oracle.infy.wookiee.grpc.contract.{HostnameServiceContract, ListenerContract}
import com.oracle.infy.wookiee.grpc.errors.Errors.WookieeGrpcError
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils.{eventLoopGroup, scalaToJavaExecutor}
import com.oracle.infy.wookiee.grpc.impl.{
  BearerTokenClientProvider,
  Fs2CloseableImpl,
  WookieeNameResolver,
  ZookeeperHostnameService
}
import com.oracle.infy.wookiee.grpc.loadbalancers.Pickers.{ConsistentHashingReadyPicker, WeightedReadyPicker}
import com.oracle.infy.wookiee.grpc.loadbalancers.WookieeLoadBalancer
import com.oracle.infy.wookiee.grpc.model.LoadBalancers.{LoadBalancingPolicy => LBPolicy}
import com.oracle.infy.wookiee.grpc.model.{Host, LoadBalancers}
import com.oracle.infy.wookiee.grpc.settings.{ChannelSettings, ClientAuthSettings, SSLClientSettings}
import fs2.Stream
import io.grpc._
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NegotiationType, NettyChannelBuilder}
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.typelevel.log4cats.Logger

import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.util.Random

final class WookieeGrpcChannel(val managedChannel: ManagedChannel) {

  def shutdown(): IO[Unit] =
    IO.blocking({
      managedChannel.shutdown()
      ()
    })

}

object WookieeGrpcChannel {

  val hashKeyCallOption: CallOptions.Key[String] = CallOptions.Key.create[String]("hash-key")

  def of(
      settings: ChannelSettings
  )(
      implicit
      logger: Logger[IO],
      runtime: unsafe.IORuntime
  ): IO[WookieeGrpcChannel] =
    for {
      listener <- Ref.of[IO, Option[ListenerContract[IO, Stream]]](None)
      fiberRef <- Ref.of[IO, Option[FiberIO[Either[WookieeGrpcError, Unit]]]](None)

      hostnameServiceSemaphore <- Semaphore[IO](1)
      nameResolverSemaphore <- Semaphore[IO](1)
      queue <- Queue.unbounded[IO, Set[Host]]
      killSwitch <- Deferred[IO, Either[Throwable, Unit]]
      cache <- Ref.of[IO, Option[CuratorCache]](None)
      _ <- addLoadBalancer(settings.lbPolicy)
      channel <- buildChannel(
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
          Fs2CloseableImpl(Stream.repeatEval(queue.take), killSwitch),
          queue.offer
        ),
        settings.serviceDiscoveryPath,
        settings.sslClientSettings,
        settings.clientAuthSettings,
        settings.clientInterceptors
      )
    } yield new WookieeGrpcChannel(channel)

  private def addLoadBalancer(lbPolicy: LBPolicy): IO[Unit] = IO {
    lbPolicy match {
      case LoadBalancers.RoundRobinPolicy => ()
      case LoadBalancers.RoundRobinHashedPolicy =>
        LoadBalancerRegistry
          .getDefaultRegistry
          .register(new LoadBalancerProvider {
            override def isAvailable: Boolean = true

            override def getPriority: Int = 5

            override def getPolicyName: String = "round_robin_hashed"

            override def newLoadBalancer(helper: LoadBalancer.Helper) =
              new WookieeLoadBalancer(helper, list => ConsistentHashingReadyPicker(list))
          })
      case LoadBalancers.RoundRobinWeightedPolicy =>
        LoadBalancerRegistry
          .getDefaultRegistry
          .register(new LoadBalancerProvider {
            override def isAvailable: Boolean = true

            override def getPriority: Int = 5

            override def getPolicyName: String = "round_robin_weighted"

            override def newLoadBalancer(helper: LoadBalancer.Helper) =
              new WookieeLoadBalancer(helper, list => WeightedReadyPicker(list))
          })
    }
  }

  // Please see https://github.com/grpc/grpc-java/issues/7133
  private def buildChannel(
      path: String,
      eventLoopGroupExecutionContext: ExecutionContext,
      channelExecutionContext: ExecutionContext,
      offloadExecutionContext: ExecutionContext,
      eventLoopGroupExecutionContextThreads: Int,
      lbPolicy: LBPolicy,
      listenerRef: Ref[IO, Option[ListenerContract[IO, Stream]]],
      semaphore: Semaphore[IO],
      fiberRef: Ref[IO, Option[FiberIO[Either[WookieeGrpcError, Unit]]]],
      hostnameServiceContract: HostnameServiceContract[IO, Stream],
      discoveryPath: String,
      maybeSSLClientSettings: Option[SSLClientSettings],
      maybeClientAuthSettings: Option[ClientAuthSettings],
      maybeInterceptors: Option[List[ClientInterceptor]]
  )(implicit logger: Logger[IO], runtime: unsafe.IORuntime): IO[ManagedChannel] = {
    for {
      // Without this the schemes can overlap due to the static nature of gRPC's APIs causing one channel to step on another
      randomScheme <- IO.blocking { Random.shuffle(('a' to 'z') ++ ('A' to 'Z')).take(12).mkString("") }
      nameResolverRegistry <- IO.blocking { NameResolverRegistry.getDefaultRegistry }
      _ <- IO.blocking {
        nameResolverRegistry.register(
          new AddressNameLoadingFactory(
            listenerRef,
            semaphore,
            fiberRef,
            hostnameServiceContract,
            discoveryPath,
            maybeSSLClientSettings,
            randomScheme
          )
        )
      }
      channelExecutorJava <- IO.blocking { scalaToJavaExecutor(channelExecutionContext) }
      offloadExecutorJava <- IO.blocking { scalaToJavaExecutor(offloadExecutionContext) }

      builder0 <- IO.blocking {
        NettyChannelBuilder
          .forTarget(s"$randomScheme://$path")
          .idleTimeout(Long.MaxValue, TimeUnit.DAYS)
          .defaultLoadBalancingPolicy(lbPolicy match {
            case LoadBalancers.RoundRobinPolicy         => "round_robin"
            case LoadBalancers.RoundRobinHashedPolicy   => "round_robin_hashed"
            case LoadBalancers.RoundRobinWeightedPolicy => "round_robin_weighted"
          })
          .usePlaintext()
          .channelFactory(() => new NioSocketChannel())
          .eventLoopGroup(eventLoopGroup(eventLoopGroupExecutionContext, eventLoopGroupExecutionContextThreads))
          .executor(channelExecutorJava)
          .offloadExecutor(offloadExecutorJava)
      }

      builder1 <- maybeSSLClientSettings
        .map { sslClientSettings =>
          logger
            .info(s"gRPC client using trust path '${sslClientSettings.sslCertificateTrustPath}'")
            .as(
              builder0
                .negotiationType(NegotiationType.TLS)
                .sslContext(buildSslContext(sslClientSettings))
            )
        }
        .getOrElse(
          builder0.pure[IO]
        )

      builder2 <- maybeClientAuthSettings
        .map { authClientSettings =>
          logger
            .info("gRPC client using bearer token authentication for [" + path + "].")
            .as(
              builder1.intercept(BearerTokenClientProvider(authClientSettings))
            )
        }
        .getOrElse(builder1.pure[IO])

      channel <- IO.blocking {
        builder2.intercept(maybeInterceptors.getOrElse(List()): _*).build()
      }
    } yield channel
  }

  private class AddressNameLoadingFactory(
      listenerRef: Ref[IO, Option[ListenerContract[IO, Stream]]],
      semaphore: Semaphore[IO],
      fiberRef: Ref[IO, Option[FiberIO[Either[WookieeGrpcError, Unit]]]],
      hostnameServiceContract: HostnameServiceContract[IO, Stream],
      discoveryPath: String,
      maybeSSLClientSettings: Option[SSLClientSettings],
      scheme: String
  )(implicit logger: Logger[IO], runtime: unsafe.IORuntime)
      extends NameResolverProvider {

    def newNameResolver(notUsedUri: URI, args: NameResolver.Args): NameResolver = {
      new WookieeNameResolver(
        listenerRef,
        semaphore,
        fiberRef,
        hostnameServiceContract,
        discoveryPath,
        maybeSSLClientSettings.map(_.serviceAuthority).getOrElse("zk")
      )
    }

    override def isAvailable = true

    override def priority(): Int = 5

    override def getDefaultScheme: String = scheme
  }

  private def buildSslContext(sslClientSettings: SSLClientSettings): SslContext = {
    val sslContextBuilder = sslClientSettings
      .mTLSOptions
      .map { options =>
        options
          .sslPassphrase
          .map { passphrase =>
            GrpcSslContexts
              .forClient
              .keyManager(
                new File(options.sslCertificateChainPath),
                new File(options.sslPrivateKeyPath),
                passphrase
              )
          }
          .getOrElse(
            GrpcSslContexts
              .forClient
              .keyManager(
                new File(options.sslCertificateChainPath),
                new File(options.sslPrivateKeyPath)
              )
          )
      }
      .getOrElse(
        GrpcSslContexts.forClient
      )

    sslClientSettings
      .sslCertificateTrustPath
      .map(trustPath => sslContextBuilder.trustManager(new File(trustPath)))
      .getOrElse(sslContextBuilder)
      .build()
  }
}
