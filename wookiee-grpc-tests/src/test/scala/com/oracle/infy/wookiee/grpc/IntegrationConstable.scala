package com.oracle.infy.wookiee.grpc

import cats.effect.std.{Dispatcher, Queue, Semaphore}
import cats.effect.unsafe.implicits.global
import cats.effect.{Deferred, IO, Ref}
import com.oracle.infy.wookiee.grpc.ZookeeperUtils._
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.grpc.contract.ListenerContract
import com.oracle.infy.wookiee.grpc.impl.{Fs2CloseableImpl, WookieeGrpcHostListener, ZookeeperHostnameService}
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.grpc.model.Host
import com.oracle.infy.wookiee.grpc.tests._
import com.oracle.infy.wookiee.grpc.utils.implicits._
import fs2.Stream
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.test.TestingServer
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

object IntegrationConstable extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    val mainECParallelism = 100
    implicit val ec: ExecutionContext = mainExecutionContext(mainECParallelism)

    val blockingEC: ExecutionContext = blockingExecutionContext("integration-test")

    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()

    val zkFake = new TestingServer()
    val connStr = zkFake.getConnectString
    val discoveryPath = "/example"
    createDiscoveryPath(connStr, discoveryPath)

    val curator = curatorFactory(connStr)
    curator.start()

    def pushMessagesFuncAndListenerFactory(
        callback: Set[Host] => IO[Unit]
    )(implicit dispatcher: Dispatcher[IO]): IO[(Set[Host] => IO[Unit], () => IO[Unit], ListenerContract[IO, Stream])] =
      for {
        queue <- Queue.unbounded[IO, Set[Host]]
        killSwitch <- Deferred[IO, Either[Throwable, Unit]]

        logger <- Slf4jLogger.create[IO]
        hostProducerCurator <- IO {
          val curator = curatorFactory(connStr)
          curator.start()
          curator
        }
        semaphore <- Semaphore[IO](1)
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
              Fs2CloseableImpl(Stream.repeatEval(queue.take), killSwitch),
              queue.offer
            )(dispatcher, logger),
            discoveryPath = discoveryPath
          )(logger)

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

    val result = Dispatcher
      .parallel[IO]
      .use { dispatcher =>
        implicit val disp: Dispatcher[IO] = dispatcher

        val grpcTests = GrpcListenerTest.tests(10, pushMessagesFuncAndListenerFactory)
        val grpcLoadBalanceTest = GrpcWeightedLoadBalanceTest.loadBalancerTest(blockingEC, mainECParallelism, curator)

        val result = runTestsAsync(
          List(
            (
              GrpcHashLoadBalanceTest.tests(blockingEC, mainECParallelism, curator),
              "Integration - GrpcHashLoadBalanceTest"
            ),
            (GrpcTLSAuthTest.tests, "Integration - GrpcTLSAuthTest"),
            (grpcTests, "Integration - GrpcTest"),
            (grpcLoadBalanceTest, "Integration - GrpcLoadBalanceTest"),
            (GrpcMultipleClientsTest.multipleClientTest, "Integration - MultipleClientTest")
          )
        )
        IO(result)
      }
    exitNegativeOnFailure(result.unsafeRunSync())
    curator.close()
    zkFake.stop()
  }
}
