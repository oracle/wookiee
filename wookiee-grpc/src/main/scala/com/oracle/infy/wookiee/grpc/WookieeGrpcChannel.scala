package com.oracle.infy.wookiee.grpc

import java.net.URI

import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.effect.{ConcurrentEffect, ContextShift, Fiber, IO}
import com.oracle.infy.wookiee.grpc.contract.{HostnameServiceContract, ListenerContract}
import com.oracle.infy.wookiee.grpc.errors.Errors.WookieeGrpcError
import com.oracle.infy.wookiee.grpc.impl.{Fs2CloseableImpl, WookieeNameResolver, ZookeeperHostnameService}
import com.oracle.infy.wookiee.model.Host
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc._
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry

import scala.concurrent.{ExecutionContext}

object WookieeGrpcChannel {

  private def addNameResolver(
      listenerRef: Ref[IO, Option[ListenerContract[IO, Stream]]],
      semaphore: Semaphore[IO],
      fiberRef: Ref[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]],
      hostnameServiceContract: HostnameServiceContract[IO, Stream],
      discoveryPath: String
  )(implicit cs: ContextShift[IO], logger: Logger[IO]) = IO {
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

  private def addLoadBalancer(): IO[Unit] = IO {
    LoadBalancerRegistry
      .getDefaultRegistry
      .register(new LoadBalancerProvider {
        override def isAvailable: Boolean = true

        override def getPriority: Int = 5

        override def getPolicyName: String = "round_robin_weighted"

        override def newLoadBalancer(helper: LoadBalancer.Helper): LoadBalancer =
          new RoundRobinWeightedLoadBalancer(helper)
      })
  }

  private def scalaToJavaExecutor(executor: ExecutionContext) = new java.util.concurrent.Executor {
    override def execute(command: Runnable): Unit = executor.execute(command)
  }

  @SuppressWarnings(
    Array(
      "scalafix:DisableSyntax.asInstanceOf"
    )
  )
  private def buildChannel(
      path: String,
      mainExecutor: ExecutionContext,
      blockingExecutor: ExecutionContext
  ): IO[ManagedChannel] = IO {
    val mainExecutorJava = scalaToJavaExecutor(mainExecutor)
    val blockingExecutorJava = scalaToJavaExecutor(blockingExecutor)
    ManagedChannelBuilder
      .forTarget(s"zookeeper://$path")
      .asInstanceOf[NettyChannelBuilder]
      .defaultLoadBalancingPolicy("round_robin_weighted")
      .usePlaintext()
      .executor(mainExecutorJava)
      .offloadExecutor(blockingExecutorJava)
      .build()
  }

  def of(
      zookeeperQuorum: String,
      serviceDiscoveryPath: String,
      mainExecutor: ExecutionContext,
      blockingExecutor: ExecutionContext
  )(
      implicit cs: ContextShift[IO],
      concurrent: ConcurrentEffect[IO]
  ): IO[ManagedChannel] = {

    val retryPolicy = new ExponentialBackoffRetry(1000, 3)
    for {
      logger <- Slf4jLogger.create[IO]
      listener <- Ref.of[IO, Option[ListenerContract[IO, Stream]]](None)
      fiberRef <- Ref.of[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]](None)
      curator <- Ref.of[IO, CuratorFramework](
        CuratorFrameworkFactory
          .builder()
          .runSafeService(scalaToJavaExecutor(blockingExecutor))
          .connectString(zookeeperQuorum)
          .retryPolicy(retryPolicy)
          .build()
      )
      hostnameServiceSemaphore <- Semaphore(1)
      nameResolverSemaphore <- Semaphore(1)
      queue <- Queue.unbounded[IO, Set[Host]]
      killSwitch <- Deferred[IO, Either[Throwable, Unit]]
      cache <- Ref.of[IO, Option[CuratorCache]](None)
      _ <- addNameResolver(
        listener,
        nameResolverSemaphore,
        fiberRef,
        new ZookeeperHostnameService(
          curator,
          cache,
          hostnameServiceSemaphore,
          Fs2CloseableImpl(queue.dequeue, killSwitch),
          queue.enqueue1
        )(concurrent, logger),
        serviceDiscoveryPath
      )(cs, logger)
      _ <- addLoadBalancer()
      channel <- buildChannel(serviceDiscoveryPath, mainExecutor, blockingExecutor)
    } yield channel
  }

  def unsafeOf(
      zookeeperQuorum: String,
      serviceDiscoveryPath: String,
      mainExecutor: ExecutionContext,
      blockingExecutor: ExecutionContext
  ): ManagedChannel = {
    implicit val cs: ContextShift[IO] = IO.contextShift(mainExecutor)
    implicit val concurrent: ConcurrentEffect[IO] = IO.ioConcurrentEffect

    of(zookeeperQuorum, serviceDiscoveryPath, mainExecutor, blockingExecutor).unsafeRunSync()
  }
}
