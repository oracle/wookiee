package com.oracle.infy.wookiee.grpc

import java.net.URI

import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Fiber, IO}
import com.oracle.infy.wookiee.grpc.contract.{HostnameServiceContract, ListenerContract}
import com.oracle.infy.wookiee.grpc.errors.Errors.WookieeGrpcError
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.impl.{Fs2CloseableImpl, WookieeNameResolver, ZookeeperHostnameService}
import com.oracle.infy.wookiee.model.LoadBalancers.{RoundRobinPolicy, LoadBalancingPolicy => LBPolicy}
import com.oracle.infy.wookiee.model.{Host, LoadBalancers}
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc._
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.CuratorCache

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class WookieeGrpcChannel(val managedChannel: ManagedChannel)(implicit cs: ContextShift[IO], blocker: Blocker) {

  def shutdown(): IO[Unit] = {
    for {
      _ <- cs.blockOn(blocker)(IO(managedChannel.shutdown()))
    } yield ()
  }

  def shutdownUnsafe(): Future[Unit] = shutdown().unsafeToFuture()
}

object WookieeGrpcChannel {

  private def addNameResolver(
      listenerRef: Ref[IO, Option[ListenerContract[IO, Stream]]],
      semaphore: Semaphore[IO],
      fiberRef: Ref[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]],
      hostnameServiceContract: HostnameServiceContract[IO, Stream],
      discoveryPath: String
  )(implicit cs: ContextShift[IO], blocker: Blocker, logger: Logger[IO]): IO[Unit] = IO {
    NameResolverRegistry
      .getDefaultRegistry
      .register(new NameResolverProvider {
        override def isAvailable = true

        override def priority() = 10

        override def getDefaultScheme = "zookeeper"

        override def newNameResolver(targetUri: URI, args: NameResolver.Args): NameResolver = {
          new WookieeNameResolver(listenerRef, semaphore, fiberRef, hostnameServiceContract, discoveryPath)
        }
      })
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

  private def scalaToJavaExecutor(executor: ExecutionContext) = new java.util.concurrent.Executor {
    override def execute(command: Runnable): Unit = executor.execute(command)
  }

  private def buildChannel(
      path: String,
      grpcChannelThreadLimit: Int,
      mainExecutor: ExecutionContext,
      blockingExecutor: ExecutionContext,
      lbPolicy: LBPolicy
  ): IO[ManagedChannel] = IO {
    val mainExecutorJava = scalaToJavaExecutor(mainExecutor)
    val blockingExecutorJava = scalaToJavaExecutor(blockingExecutor)

    NettyChannelBuilder
      .forTarget(s"zookeeper://$path")
      .defaultLoadBalancingPolicy(lbPolicy match {
        case RoundRobinPolicy                       => "round_robin"
        case LoadBalancers.RoundRobinWeightedPolicy => "round_robin_weighted"
      })
      .usePlaintext()
      .channelFactory(() => new NioSocketChannel())
      .eventLoopGroup(eventLoopGroup(blockingExecutor, grpcChannelThreadLimit))
      .executor(mainExecutorJava)
      .offloadExecutor(blockingExecutorJava)
      .build()
  }

  def of(
      zookeeperQuorum: String,
      serviceDiscoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      grpcChannelThreadLimit: Int,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext
  )(
      implicit cs: ContextShift[IO],
      concurrent: ConcurrentEffect[IO],
      blocker: Blocker,
      logger: Logger[IO]
  ): IO[WookieeGrpcChannel] = {
    of(
      zookeeperQuorum,
      serviceDiscoveryPath,
      zookeeperRetryInterval,
      zookeeperMaxRetries,
      grpcChannelThreadLimit,
      RoundRobinPolicy,
      mainExecutionContext,
      blockingExecutionContext
    )
  }

  def of(
      zookeeperQuorum: String,
      serviceDiscoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      grpcChannelThreadLimit: Int,
      lbPolicy: LBPolicy,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext
  )(
      implicit cs: ContextShift[IO],
      concurrent: ConcurrentEffect[IO],
      blocker: Blocker,
      logger: Logger[IO]
  ): IO[WookieeGrpcChannel] = {

    val retryPolicy = exponentialBackoffRetry(zookeeperRetryInterval, zookeeperMaxRetries)

    for {
      listener <- Ref.of[IO, Option[ListenerContract[IO, Stream]]](None)
      fiberRef <- Ref.of[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]](None)
      curator <- cs.blockOn(blocker)(
        Ref.of[IO, CuratorFramework](
          curatorFramework(zookeeperQuorum, blockingExecutionContext, retryPolicy)
        )
      )
      hostnameServiceSemaphore <- Semaphore(1)
      nameResolverSemaphore <- Semaphore(1)
      queue <- Queue.unbounded[IO, Set[Host]]
      killSwitch <- Deferred[IO, Either[Throwable, Unit]]
      cache <- Ref.of[IO, Option[CuratorCache]](None)
      _ <- cs.blockOn(blocker)(
        addNameResolver(
          listener,
          nameResolverSemaphore,
          fiberRef,
          new ZookeeperHostnameService(
            curator,
            cache,
            hostnameServiceSemaphore,
            Fs2CloseableImpl(queue.dequeue, killSwitch),
            queue.enqueue1
          )(blocker, cs, concurrent, logger),
          serviceDiscoveryPath
        )(cs, blocker, logger)
      )
      _ <- addLoadBalancer(lbPolicy)
      channel <- cs.blockOn(blocker)(
        buildChannel(
          serviceDiscoveryPath,
          grpcChannelThreadLimit,
          mainExecutionContext,
          blockingExecutionContext,
          lbPolicy
        )
      )
    } yield new WookieeGrpcChannel(channel)
  }

  def unsafeOf(
      zookeeperQuorum: String,
      serviceDiscoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      grpcChannelThreadLimit: Int,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext
  ): WookieeGrpcChannel = {
    unsafeOf(
      zookeeperQuorum,
      serviceDiscoveryPath,
      zookeeperRetryInterval,
      zookeeperMaxRetries,
      grpcChannelThreadLimit,
      RoundRobinPolicy,
      mainExecutionContext,
      blockingExecutionContext
    )
  }

  def unsafeOf(
      zookeeperQuorum: String,
      serviceDiscoveryPath: String,
      zookeeperRetryInterval: FiniteDuration,
      zookeeperMaxRetries: Int,
      grpcChannelThreadLimit: Int,
      lbPolicy: LBPolicy,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext
  ): WookieeGrpcChannel = {
    implicit val cs: ContextShift[IO] = IO.contextShift(mainExecutionContext)
    implicit val concurrent: ConcurrentEffect[IO] = IO.ioConcurrentEffect
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()
    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingExecutionContext)

    of(
      zookeeperQuorum,
      serviceDiscoveryPath,
      zookeeperRetryInterval,
      zookeeperMaxRetries,
      grpcChannelThreadLimit,
      lbPolicy,
      mainExecutionContext,
      blockingExecutionContext
    ).unsafeRunSync()
  }
}
