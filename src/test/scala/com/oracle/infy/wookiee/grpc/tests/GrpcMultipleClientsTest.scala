package com.oracle.infy.wookiee.grpc.tests

import cats.effect.{Blocker, ContextShift, IO, Timer}
import com.oracle.infy.wookiee.grpc.common.UTestScalaCheck
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings.{ChannelSettings, ServerSettings}
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer, ZookeeperUtils}
import com.oracle.infy.wookiee.grpc.model.LoadBalancers.RoundRobinPolicy
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import com.oracle.infy.wookiee.myService2.MyService2Grpc.MyService2
import com.oracle.infy.wookiee.myService2.{HelloRequest2, HelloResponse2, MyService2Grpc}
import com.oracle.infy.wookiee.grpc.utils.implicits.MultiversalEquality
import org.typelevel.log4cats.Logger
import io.grpc.ServerServiceDefinition
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.test.TestingServer
import utest.{Tests, test}

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ForkJoinPool, ThreadFactory}
import scala.concurrent.{ExecutionContext, Future}

object GrpcMultipleClientsTest extends UTestScalaCheck {

  def multipleClientTest(
      implicit implicitEC: ExecutionContext,
      cs: ContextShift[IO],
      blocker: Blocker,
      timer: Timer[IO],
      logger: Logger[IO]
  ): Tests = {

    val testBody = {

      val bossThreads = 10
      val mainECParallelism = 10

      val uncaughtExceptionHandler = new UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, e: Throwable): Unit =
          logger.error(e)("Got an uncaught exception on thread " ++ t.getName).unsafeRunSync()
      }

      val tf = new ThreadFactory {
        override def newThread(r: Runnable): Thread = {
          val t = new Thread(r)
          t.setName("blocking-" ++ t.getId.toString)
          t.setUncaughtExceptionHandler(uncaughtExceptionHandler)
          t.setDaemon(true)
          t
        }
      }

      // The blocking execution context must create daemon threads if you want your app to shutdown
      val blockingEC1 = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(tf))
      val blockingEC2 = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(tf))
      // This is the execution context used to execute your application specific code
      implicit val mainEC1: ExecutionContext = ExecutionContext.fromExecutor(
        new ForkJoinPool(
          mainECParallelism,
          ForkJoinPool.defaultForkJoinWorkerThreadFactory,
          uncaughtExceptionHandler,
          true
        )
      )

      val mainEC2: ExecutionContext = ExecutionContext.fromExecutor(
        new ForkJoinPool(
          mainECParallelism,
          ForkJoinPool.defaultForkJoinWorkerThreadFactory,
          uncaughtExceptionHandler,
          true
        )
      )

      val zookeeperDiscoveryPath1 = "/multi/discovery"
      val zookeeperDiscoveryPath2 = "/multi/example"

      // This is just to demo, use an actual Zookeeper quorum.
      val zkFake = new TestingServer()
      val connStr = zkFake.getConnectString
      val curator: CuratorFramework = ZookeeperUtils.curatorFactory(connStr)
      curator.start()

      val ssd: ServerServiceDefinition = MyService.bindService(
        (request: HelloRequest) => {
          Future.successful(HelloResponse("Hello " ++ request.name))
        },
        mainEC1
      )

      val ssd2: ServerServiceDefinition = MyService2.bindService(
        (request: HelloRequest2) => {
          Future.successful(HelloResponse2("Hello2 " ++ request.name))
        },
        mainEC2
      )

      val serverSettings1F: ServerSettings = ServerSettings(
        discoveryPath = zookeeperDiscoveryPath1,
        serverServiceDefinition = ssd,
        // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
        // If you are running this locally, its better to explicitly set the hostname
        host = Host(0, "localhost", 9097, HostMetadata(0, quarantined = false)),
        sslServerSettings = None,
        authSettings = None,
        bossExecutionContext = mainEC1,
        workerExecutionContext = mainEC1,
        applicationExecutionContext = mainEC1,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism,
        curatorFramework = curator
      )

      val serverSettingsF2: ServerSettings = ServerSettings(
        discoveryPath = zookeeperDiscoveryPath2,
        serverServiceDefinition = ssd2,
        // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
        // If you are running this locally, its better to explicitly set the hostname
        host = Host(0, "localhost", 9096, HostMetadata(0, quarantined = false)),
        sslServerSettings = None,
        authSettings = None,
        bossExecutionContext = mainEC2,
        workerExecutionContext = mainEC2,
        applicationExecutionContext = mainEC2,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism,
        curatorFramework = curator
      )

      val serverF1 = WookieeGrpcServer.start(serverSettings1F)
      val serverF2 = WookieeGrpcServer.start(serverSettingsF2)

      val wookieeGrpcChannel1 = WookieeGrpcChannel.of(
        ChannelSettings(
          serviceDiscoveryPath = zookeeperDiscoveryPath1,
          eventLoopGroupExecutionContext = blockingEC1,
          channelExecutionContext = mainEC1,
          offloadExecutionContext = blockingEC1,
          eventLoopGroupExecutionContextThreads = bossThreads,
          lbPolicy = RoundRobinPolicy,
          curatorFramework = curator,
          sslClientSettings = None,
          clientAuthSettings = None
        )
      )

      val wookieeGrpcChannel2 = WookieeGrpcChannel.of(
        ChannelSettings(
          serviceDiscoveryPath = zookeeperDiscoveryPath2,
          eventLoopGroupExecutionContext = blockingEC2,
          channelExecutionContext = mainEC2,
          offloadExecutionContext = blockingEC2,
          eventLoopGroupExecutionContextThreads = bossThreads,
          lbPolicy = RoundRobinPolicy,
          curatorFramework = curator,
          sslClientSettings = None,
          clientAuthSettings = None
        )
      )

      val gRPCResponseF = for {

        c1 <- wookieeGrpcChannel1
        c2 <- wookieeGrpcChannel2
        stub1 = MyServiceGrpc.stub(c1.managedChannel)
        stub2 = MyService2Grpc.stub(c2.managedChannel)
        server1 <- serverF1
        server2 <- serverF2
        resp <- IO.fromFuture(IO {
          stub1.greet(HelloRequest("world!"))
        })
        resp2 <- IO.fromFuture(IO {
          stub2.greet2(HelloRequest2("world!"))
        })
        _ <- c1.shutdown()
        _ <- c2.shutdown()
        _ <- server2.shutdown()
        _ <- server1.shutdown()
        _ <- IO(curator.close())
        _ <- IO(zkFake.close())
      } yield resp.resp === "Hello world!" && resp2.resp === "Hello2 world!"

      gRPCResponseF.unsafeToFuture()

    }

    Tests {
      test("least busy server handles request") {
        testBody.map(assert)
      }
    }
  }
}
