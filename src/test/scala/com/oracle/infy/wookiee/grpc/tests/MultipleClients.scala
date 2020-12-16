package com.oracle.infy.wookiee.grpc.tests

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ForkJoinPool, ThreadFactory}

import cats.effect.IO
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer}
import com.oracle.infy.wookiee.grpc.json.ServerSettings
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.model.LoadBalancers.RoundRobinPolicy
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService2.{HelloRequest2, HelloResponse2, MyService2Grpc}
import com.oracle.infy.wookiee.myService2.MyService2Grpc.MyService2
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc.ServerServiceDefinition
import org.apache.curator.test.TestingServer

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object MultipleClients {
  def main(args: Array[String]): Unit = {
    val bossThreads = 10
    val mainECParallelism = 10

    // wookiee-grpc is written using functional concepts. One key concept is side-effect management/referential transparency
    // We use cats-effect (https://typelevel.org/cats-effect/) internally.
    // If you want to use cats-effect, you can use the methods that return IO[_]. Otherwise, use the methods prefixed with `unsafe`.
    // When using `unsafe` methods, you are expected to handle any exceptions
    val logger = Slf4jLogger.create[IO].unsafeRunSync()

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

    val mainEC2: ExecutionContext = ExecutionContext.fromExecutor(
      new ForkJoinPool(
        mainECParallelism,
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        uncaughtExceptionHandler,
        true
      )
    )

    val zookeeperDiscoveryPath = "/discovery"
    val zookeeperDiscoveryPath2 = "/example"

    // This is just to demo, use an actual Zookeeper quorum.
    val zkFake = new TestingServer()
    val connStr = zkFake.getConnectString

    val zkFake2 = new TestingServer()
    val connStr2 = zkFake2.getConnectString

    val ssd: ServerServiceDefinition = MyService.bindService(
      (request: HelloRequest) => {
        println("received request")
        Future.successful(HelloResponse("Hello " ++ request.name))
      },
      mainEC
    )

    val ssd2: ServerServiceDefinition = MyService2.bindService(
      (request: HelloRequest2) => {
        println("received request")
        Future.successful(HelloResponse2("Hello2 " ++ request.name))
      },
      mainEC2
    )

    val serverSettingsF: ServerSettings = ServerSettings(
      zookeeperQuorum = connStr,
      discoveryPath = zookeeperDiscoveryPath,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      serverServiceDefinition = ssd,
      port = 9091,
      // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
      // If you are running this locally, its better to explicitly set the hostname
      host = IO(Host(0, "localhost", 9091, HostMetadata(0, quarantined = false))),
      bossExecutionContext = mainEC,
      workerExecutionContext = mainEC,
      applicationExecutionContext = mainEC,
      zookeeperBlockingExecutionContext = blockingEC,
      timerExecutionContext = blockingEC,
      bossThreads = bossThreads,
      workerThreads = mainECParallelism
    )

    val serverSettingsF2: ServerSettings = ServerSettings(
      zookeeperQuorum = connStr2,
      discoveryPath = zookeeperDiscoveryPath2,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      serverServiceDefinition = ssd2,
      port = 9090,
      // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
      // If you are running this locally, its better to explicitly set the hostname
      host = IO(Host(0, "localhost", 9091, HostMetadata(0, quarantined = false))),
      bossExecutionContext = mainEC2,
      workerExecutionContext = mainEC2,
      applicationExecutionContext = mainEC2,
      zookeeperBlockingExecutionContext = blockingEC,
      timerExecutionContext = blockingEC,
      bossThreads = bossThreads,
      workerThreads = mainECParallelism
    )

    val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(serverSettingsF)
    val serverF2: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(serverSettingsF2)

    val wookieeGrpcChannel: WookieeGrpcChannel = WookieeGrpcChannel.unsafeOf(
      zookeeperQuorum = connStr,
      serviceDiscoveryPath = zookeeperDiscoveryPath,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      mainExecutionContext = mainEC,
      blockingExecutionContext = blockingEC,
      zookeeperBlockingExecutionContext = blockingEC,
      eventLoopGroupExecutionContext = blockingEC,
      channelExecutionContext = mainEC,
      offloadExecutionContext = blockingEC,
      eventLoopGroupExecutionContextThreads = bossThreads,
      lbPolicy = RoundRobinPolicy
    )

    val wookieeGrpcChannel2: WookieeGrpcChannel = WookieeGrpcChannel.unsafeOf(
      zookeeperQuorum = connStr2,
      serviceDiscoveryPath = zookeeperDiscoveryPath2,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      mainExecutionContext = mainEC,
      blockingExecutionContext = blockingEC,
      zookeeperBlockingExecutionContext = blockingEC,
      eventLoopGroupExecutionContext = blockingEC,
      channelExecutionContext = mainEC,
      offloadExecutionContext = blockingEC,
      eventLoopGroupExecutionContextThreads = bossThreads,
      lbPolicy = RoundRobinPolicy
    )

    val stub: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(wookieeGrpcChannel.managedChannel)

    val stub2: MyService2Grpc.MyService2Stub = MyService2Grpc.stub(wookieeGrpcChannel2.managedChannel)

    val gRPCResponseF: Future[(HelloResponse, HelloResponse2)] = for {
      server <- serverF
      server2 <- serverF2
      resp <- stub.greet(HelloRequest("world!"))
      resp2 <- stub2.greet2(HelloRequest2("world!"))
      _ <- wookieeGrpcChannel.shutdownUnsafe()
      _ <- wookieeGrpcChannel2.shutdownUnsafe()
      _ <- server2.shutdownUnsafe()
      _ <- server.shutdownUnsafe()
    } yield (resp, resp2)

    println(Await.result(gRPCResponseF, Duration.Inf))
    zkFake.close()
    ()
  }
}
