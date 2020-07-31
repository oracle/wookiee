package com.oracle.infy.wookiee.grpc

import java.net.URI
import java.util.concurrent.Executor

import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Fiber, IO}
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
import io.grpc.netty.shaded.io.netty.channel
import io.grpc.netty.shaded.io.netty.channel.ChannelFactory
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry

import scala.concurrent.ExecutionContext

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

  private def scalaToJavaExecutor(executor: ExecutionContext): Executor = new java.util.concurrent.Executor {
    override def execute(command: Runnable): Unit = executor.execute(command)
  }

  private def buildChannel(
      path: String,
      grpcChannelThreadLimit: Int,
      dispatcherExecutor: ExecutionContext,
      mainExecutor: ExecutionContext,
      blockingExecutor: ExecutionContext
  ): IO[ManagedChannel] = IO {
    val dispatchExecutorJava = scalaToJavaExecutor(dispatcherExecutor)
    val mainExecutorJava = scalaToJavaExecutor(mainExecutor)
    val blockingExecutorJava = scalaToJavaExecutor(blockingExecutor)

    NettyChannelBuilder
      .forTarget(s"zookeeper://$path")
      .defaultLoadBalancingPolicy("round_robin")
      .usePlaintext()
      .channelFactory(new ChannelFactory[channel.Channel] {
        override def newChannel(): channel.Channel = new NioSocketChannel()
      })
      .eventLoopGroup(new NioEventLoopGroup(grpcChannelThreadLimit, dispatchExecutorJava))
      .executor(mainExecutorJava)
      .offloadExecutor(blockingExecutorJava)
      .build()
  }

  def of(
      zookeeperQuorum: String,
      serviceDiscoveryPath: String,
      grpcChannelThreadLimit: Int,
      dispatcherExecutionContext: ExecutionContext,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext
  )(
      implicit cs: ContextShift[IO],
      concurrent: ConcurrentEffect[IO]
  ): IO[ManagedChannel] = {

    val blocker = Blocker.liftExecutionContext(blockingExecutionContext)
    val retryPolicy = new ExponentialBackoffRetry(1000, 3)

    for {
      logger <- Slf4jLogger.create[IO]
      listener <- Ref.of[IO, Option[ListenerContract[IO, Stream]]](None)
      fiberRef <- Ref.of[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]](None)
      curator <- cs.blockOn(blocker)(
        Ref.of[IO, CuratorFramework](
          CuratorFrameworkFactory
            .builder()
            .runSafeService(scalaToJavaExecutor(blockingExecutionContext))
            .connectString(zookeeperQuorum)
            .retryPolicy(retryPolicy)
            .build()
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
      channel <- cs.blockOn(blocker)(
        buildChannel(
          serviceDiscoveryPath,
          grpcChannelThreadLimit,
          dispatcherExecutionContext,
          mainExecutionContext,
          blockingExecutionContext
        )
      )
    } yield channel
  }

  def unsafeOf(
      zookeeperQuorum: String,
      serviceDiscoveryPath: String,
      grpcChannelThreadLimit: Int,
      dispatcherExecutionContext: ExecutionContext,
      mainExecutionContext: ExecutionContext,
      blockingExecutionContext: ExecutionContext
  ): ManagedChannel = {
    implicit val cs: ContextShift[IO] = IO.contextShift(mainExecutionContext)
    implicit val concurrent: ConcurrentEffect[IO] = IO.ioConcurrentEffect

    of(
      zookeeperQuorum,
      serviceDiscoveryPath,
      grpcChannelThreadLimit,
      dispatcherExecutionContext,
      mainExecutionContext,
      blockingExecutionContext
    ).unsafeRunSync()
  }
}
