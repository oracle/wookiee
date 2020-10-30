package com.oracle.infy.wookiee.grpc.tests

import java.util.Random

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, IO}
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import com.oracle.infy.wookiee.grpc.IntegrationConstable.{blockingExecutionContext, mainExecutionContext}
import com.oracle.infy.wookiee.grpc.WookieeGrpcServer
import com.oracle.infy.wookiee.grpc.ZookeeperUtils.{createDiscoveryPath, curatorFactory}
import com.oracle.infy.wookiee.grpc.contract.ListenerContract
import com.oracle.infy.wookiee.grpc.impl.{Fs2CloseableImpl, WookieeGrpcHostListener, ZookeeperHostnameService}
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.model.Host
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse}
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc.ServerServiceDefinition
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.test.TestingServer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object UpdateLoadTest {

  def main(args: Array[String]): Unit = {
    val _ = args
    val randomLoad: Random = new Random()
    val load = randomLoad.nextInt(10)
    val mainECParallelism = 100
    implicit val ec: ExecutionContext = mainExecutionContext(mainECParallelism)
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    implicit val concurrent: ConcurrentEffect[IO] = IO.ioConcurrentEffect
    val blockingEC: ExecutionContext = blockingExecutionContext("integration-test")
    val blocker = Blocker.liftExecutionContext(blockingEC)

    val zkFake = new TestingServer()
    val connStr = "localhost:1081" // todo: pretty sure this is where zk runs but verify
    val discoveryPath = "/example"
    createDiscoveryPath(connStr, discoveryPath)

    def pushMessagesFuncAndListenerFactory(
        callback: Set[Host] => IO[Unit]
    ): IO[(Set[Host] => IO[Unit], () => IO[Unit], ListenerContract[IO, Stream])] = {
      for {
        queue <- Queue.unbounded[IO, Set[Host]]
        killSwitch <- Deferred[IO, Either[Throwable, Unit]]

        logger <- Slf4jLogger.create[IO]
        hostConsumerCuratorRef <- Ref.of[IO, CuratorFramework](curatorFactory(connStr))
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
              hostConsumerCuratorRef,
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

    val ssd: ServerServiceDefinition = MyService.bindService(
      (request: HelloRequest) => {
        Future.successful(HelloResponse("Hello1 " ++ request.name))
      },
      ec
    )

    val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(
      zookeeperQuorum = connStr,
      discoveryPath = discoveryPath,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      serverServiceDefinition = ssd,
      port = 8080,
      // Host is given a randomly generated load number: this is used to determine which server is the least busy.
      localhost = Host(0, "localhost", 8080, Map[String, String](("load", load.toString))),
      mainExecutionContext = ec,
      blockingExecutionContext = blockingEC,
      bossThreads = 2,
      mainExecutionContextThreads = mainECParallelism
    )

    for {
      server <- serverF
      //thread.sleep in order to observe load was assigned
      _ <- Future(server.assignLoad(load - 5))
    } yield ()
    ()
  }
}
