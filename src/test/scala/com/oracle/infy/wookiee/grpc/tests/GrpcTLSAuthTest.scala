package com.oracle.infy.wookiee.grpc.tests

import cats.effect.{Blocker, ContextShift, IO, Timer}
import com.oracle.infy.wookiee.grpc.settings.{ChannelSettings, ClientAuthSettings, ServerSettings, ServiceAuthSettings}
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer, ZookeeperUtils}
import com.oracle.infy.wookiee.model.LoadBalancers.RoundRobinPolicy
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import com.oracle.infy.wookiee.utils.implicits.MultiversalEquality
import io.chrisdavenport.log4cats.Logger
import io.grpc.ServerServiceDefinition
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.test.TestingServer
import utest.{Tests, test}

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ForkJoinPool, ThreadFactory}
import scala.concurrent.{ExecutionContext, Future}

object GrpcTLSAuthTest {

  def tests(
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
        override def uncaughtException(t: Thread, e: Throwable): Unit = {
          logger.error(e)("Got an uncaught exception on thread " ++ t.getName).unsafeRunSync()
        }
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
      val blockingEC = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool(tf))

      // This is the execution context used to execute your application specific code
      implicit val mainEC: ExecutionContext = ExecutionContext.fromExecutor(
        new ForkJoinPool(
          mainECParallelism,
          ForkJoinPool.defaultForkJoinWorkerThreadFactory,
          uncaughtExceptionHandler,
          true
        )
      )

      val zookeeperDiscoveryPath1 = "/multi/discovery"

      val zkFake = new TestingServer()
      val connStr = zkFake.getConnectString
      val curator: CuratorFramework = ZookeeperUtils.curatorFactory(connStr)
      curator.start()

      val ssd: ServerServiceDefinition = MyService.bindService(
        (request: HelloRequest) => {
          Future.successful(HelloResponse("Hello " ++ request.name))
        },
        mainEC
      )

      val token = "my-token"

      val serverSettings: ServerSettings = ServerSettings(
        discoveryPath = zookeeperDiscoveryPath1,
        serverServiceDefinition = ssd,
        host = Host(0, "localhost", 9098, HostMetadata(0, quarantined = false)),
        sslServerSettings = None,
        authSettings = Some(ServiceAuthSettings(token)),
        bossExecutionContext = mainEC,
        workerExecutionContext = mainEC,
        applicationExecutionContext = mainEC,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism,
        curatorFramework = curator
      )

      val wookieeGrpcServer = WookieeGrpcServer.start(serverSettings)

      val wookieeGrpcChannel = WookieeGrpcChannel.of(
        ChannelSettings(
          serviceDiscoveryPath = zookeeperDiscoveryPath1,
          eventLoopGroupExecutionContext = blockingEC,
          channelExecutionContext = mainEC,
          offloadExecutionContext = blockingEC,
          eventLoopGroupExecutionContextThreads = bossThreads,
          lbPolicy = RoundRobinPolicy,
          curatorFramework = curator,
          sslClientSettings = None,
          clientAuthSettings = Some(ClientAuthSettings(token))
        )
      )

      val gRPCResponseF = for {

        server <- wookieeGrpcServer
        channel <- wookieeGrpcChannel

        stub = MyServiceGrpc.stub(channel.managedChannel)

        resp <- IO.fromFuture(IO {
          stub.greet(HelloRequest("world!"))
        })

        _ <- channel.shutdown()
        _ <- server.shutdown()
        _ <- IO(curator.close())
        _ <- IO(zkFake.close())
      } yield resp.resp === "Hello world!"

      gRPCResponseF.unsafeToFuture()

    }

    Tests {
      test("Communicate over TLS using Authentication") {
        testBody.map(assert)
      }
    }
  }
}
