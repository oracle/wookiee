package com.oracle.infy.wookiee.grpc.tests

import cats.effect.IO
import com.oracle.infy.wookiee.grpc.impl.TestInterceptors.{
  TestClientInterceptor,
  TestServerInterceptor,
  clientInterceptorHit,
  serverInterceptorHit
}
import com.oracle.infy.wookiee.grpc.model.LoadBalancers.RoundRobinPolicy
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings.{ChannelSettings, ClientAuthSettings, ServerSettings, ServiceAuthSettings}
import com.oracle.infy.wookiee.grpc.utils.implicits.MultiversalEquality
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer, ZookeeperUtils}
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import io.grpc.ServerServiceDefinition
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.test.TestingServer
import org.typelevel.log4cats.Logger
import utest.{Tests, test}

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ForkJoinPool, ThreadFactory}
import scala.concurrent.{ExecutionContext, Future}
import cats.effect.Temporal

object GrpcTLSAuthTest {

  def tests(
      implicit implicitEC: ExecutionContext,
      timer: Temporal[IO],
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

      val zookeeperDiscoveryPath1 = "/tls"

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
        serverInterceptors = Some(List(new TestServerInterceptor())),
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
          clientAuthSettings = Some(ClientAuthSettings(token)),
          clientInterceptors = Some(List(new TestClientInterceptor()))
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
      } yield resp.resp === "Hello world!" && clientInterceptorHit.get() && serverInterceptorHit.get()

      gRPCResponseF.unsafeToFuture()

    }

    Tests {
      test("Communicate over TLS using Authentication") {
        testBody.map(assert)
      }
    }
  }
}
