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

import scala.concurrent.ExecutionContext

object WookieeGrpcChannel {

  private def addNameResolver(
      listenerRef: Ref[IO, Option[ListenerContract[IO, Stream]]],
      semaphore: Semaphore[IO],
      fiberRef: Ref[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]],
      hostnameServiceContract: HostnameServiceContract[IO, Stream],
      discoveryPath: String
  )(implicit cs: ContextShift[IO], logger: Logger[IO]): IO[Unit] = IO {
    NameResolverRegistry
      .getDefaultRegistry
      .register(new NameResolverProvider {
        override def isAvailable: Boolean = true

        override def priority(): Int = 10

        override def getDefaultScheme: String = "zookeeper"

        override def newNameResolver(targetUri: URI, args: NameResolver.Args): NameResolver = {
          new WookieeNameResolver(listenerRef, semaphore, fiberRef, hostnameServiceContract, discoveryPath)
        }
      })
  }

  @SuppressWarnings(
    Array(
      "scalafix:DisableSyntax.asInstanceOf"
    )
  )
  private def buildChannel(path: String): IO[ManagedChannel] = IO {
    ManagedChannelBuilder
      .forTarget(s"zookeeper://$path")
      .asInstanceOf[NettyChannelBuilder]
      .defaultLoadBalancingPolicy("round_robin")
      .usePlaintext()
      .build()
  }

  def of(zookeeperQuorum: String, serviceDiscoveryPath: String)(
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
      channel <- buildChannel(serviceDiscoveryPath)
    } yield channel
  }

  def unsafeOf(zookeeperQuorum: String, serviceDiscoveryPath: String)(implicit ec: ExecutionContext): ManagedChannel = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    implicit val concurrent: ConcurrentEffect[IO] = IO.ioConcurrentEffect

    of(zookeeperQuorum, serviceDiscoveryPath).unsafeRunSync()
  }
}
