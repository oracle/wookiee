package com.oracle.infy.wookiee.grpc

import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Fiber, IO}
import com.oracle.infy.wookiee.grpc.contract.{HostnameServiceContract, ListenerContract}
import com.oracle.infy.wookiee.grpc.errors.Errors.WookieeGrpcError
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils._
import com.oracle.infy.wookiee.grpc.impl.{Fs2CloseableImpl, WookieeNameResolver, ZookeeperHostnameService}
import com.oracle.infy.wookiee.grpc.settings.ChannelSettings
import com.oracle.infy.wookiee.model.LoadBalancers.{RoundRobinPolicy, LoadBalancingPolicy => LBPolicy}
import com.oracle.infy.wookiee.model.{Host, LoadBalancers}
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc._
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.CuratorCache

import java.net.URI
import java.util.concurrent.{Executor, TimeUnit}
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

  private def scalaToJavaExecutor(executor: ExecutionContext): Executor =
    (command: Runnable) => executor.execute(command)

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
      discoveryPath: String
  )(implicit cs: ContextShift[IO], blocker: Blocker, logger: Logger[IO]): IO[ManagedChannel] = IO {
    val channelExecutorJava = scalaToJavaExecutor(channelExecutionContext)
    val offloadExecutorJava = scalaToJavaExecutor(offloadExecutionContext)

    val _ =
      (channelExecutorJava, offloadExecutorJava, eventLoopGroupExecutionContext, eventLoopGroupExecutionContextThreads)

    NettyChannelBuilder
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
        case RoundRobinPolicy                       => "round_robin"
        case LoadBalancers.RoundRobinWeightedPolicy => "round_robin_weighted"
      })
      .usePlaintext()
      // TODO: Figure out why this is not working
      //      .channelFactory(() => new NioSocketChannel())
      //      .eventLoopGroup(eventLoopGroup(eventLoopGroupExecutionContext, eventLoopGroupExecutionContextThreads))
      //      .executor(channelExecutorJava)
      //      .offloadExecutor(offloadExecutorJava)
      .build()
  }

  def of(
      settings: ChannelSettings
  )(
      implicit cs: ContextShift[IO],
      concurrent: ConcurrentEffect[IO],
      blocker: Blocker,
      logger: Logger[IO]
  ): IO[WookieeGrpcChannel] = {

    val retryPolicy = exponentialBackoffRetry(settings.zookeeperRetryInterval, settings.zookeeperMaxRetries)

    for {
      listener <- Ref.of[IO, Option[ListenerContract[IO, Stream]]](None)
      fiberRef <- Ref.of[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]](None)
      curator <- cs.blockOn(blocker)(
        Ref.of[IO, CuratorFramework](
          curatorFramework(settings.zookeeperQuorum, settings.zookeeperBlockingExecutionContext, retryPolicy)
        )
      )

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
            curator,
            cache,
            hostnameServiceSemaphore,
            Fs2CloseableImpl(queue.dequeue, killSwitch),
            queue.enqueue1
          ),
          settings.serviceDiscoveryPath
        )
      )
    } yield new WookieeGrpcChannel(channel)
  }

  def unsafeOf(
      settings: ChannelSettings,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext
  ): WookieeGrpcChannel = {
    implicit val cs: ContextShift[IO] = IO.contextShift(mainExecutionContext)
    implicit val concurrent: ConcurrentEffect[IO] = IO.ioConcurrentEffect
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()
    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingExecutionContext)

    of(settings).unsafeRunSync()
  }
}
