package com.oracle.infy.wookiee.grpc

import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, IO, Timer}
import com.oracle.infy.wookiee.grpc.ZookeeperUtils._
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.grpc.contract.ListenerContract
import com.oracle.infy.wookiee.grpc.impl.{Fs2CloseableImpl, WookieeGrpcHostListener, ZookeeperHostnameService}
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.grpc.tests.{GrpcListenerTest, GrpcLoadBalanceTest, MultipleClientsTest}
import com.oracle.infy.wookiee.model.Host
import com.oracle.infy.wookiee.utils.implicits._
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.test.TestingServer

import scala.concurrent.ExecutionContext

object IntegrationConstable extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    val mainECParallelism = 100
    implicit val ec: ExecutionContext = mainExecutionContext(mainECParallelism)
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    implicit val concurrent: ConcurrentEffect[IO] = IO.ioConcurrentEffect
    val blockingEC: ExecutionContext = blockingExecutionContext("integration-test")
    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingEC)

    implicit val timer: Timer[IO] = IO.timer(blockingEC)
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()

    val zkFake = new TestingServer()
    val connStr = zkFake.getConnectString
    val discoveryPath = "/example"
    createDiscoveryPath(connStr, discoveryPath)

    val curator = curatorFactory(connStr)
    curator.start()


    def pushMessagesFuncAndListenerFactory(
        callback: Set[Host] => IO[Unit]
    ): IO[(Set[Host] => IO[Unit], () => IO[Unit], ListenerContract[IO, Stream])] = {
      for {
        queue <- Queue.unbounded[IO, Set[Host]]
        killSwitch <- Deferred[IO, Either[Throwable, Unit]]

        logger <- Slf4jLogger.create[IO]
        hostProducerCurator <- IO {
          val curator = curatorFactory(connStr)
          curator.start()
          curator
        }
        semaphore <- Semaphore(1)
        cache <- Ref.of[IO, Option[CuratorCache]](None)

      } yield {

        val pushMessagesFunc = { hosts: Set[Host] =>
          IO {
            hosts.foreach { host =>
              val nodePath = s"$discoveryPath/${host.address}"
              hostProducerCurator.create().orSetData().forPath(nodePath, HostSerde.serialize(host))
            }
          }
        }

        val listener: ListenerContract[IO, Stream] =
          new WookieeGrpcHostListener(
            callback,
            new ZookeeperHostnameService(
              curator,
              cache,
              semaphore,
              Fs2CloseableImpl(queue.dequeue, killSwitch),
              queue.enqueue1
            )(blocker, IO.contextShift(ec), concurrent, logger),
            discoveryPath = discoveryPath
          )(cs, blocker, logger)

        val cleanup: () => IO[Unit] = () => {
          IO {
            hostProducerCurator.getChildren.forPath(discoveryPath).asScala.foreach { child =>
              hostProducerCurator.delete().guaranteed().forPath(s"$discoveryPath/$child")
            }
            hostProducerCurator.close()
            ()
          }
        }
        (pushMessagesFunc, cleanup, listener)
      }
    }

    val grpcTests = GrpcListenerTest.tests(10, pushMessagesFuncAndListenerFactory)
    val grpcLoadBalanceTest = GrpcLoadBalanceTest.loadBalancerTest(blockingEC, mainECParallelism, curator)


    val result = runTestsAsync(
      List(
        (grpcTests, "Integration - GrpcTest"),
        (grpcLoadBalanceTest, "Integration - GrpcLoadBalanceTest"),
        (MultipleClientsTest.multipleClientTest, "Integration - MultipleClientTest")
      )
    )
    curator.close()
    zkFake.stop()
    exitNegativeOnFailure(result)
  }
}
