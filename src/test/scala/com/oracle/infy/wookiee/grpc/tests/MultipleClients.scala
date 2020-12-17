package com.oracle.infy.wookiee.grpc.tests

import cats.effect.{Blocker, ContextShift, IO, Timer}
import com.oracle.infy.wookiee.grpc.settings.{ChannelSettings, ServerSettings}
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer}
import com.oracle.infy.wookiee.model.LoadBalancers.RoundRobinPolicy
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import com.oracle.infy.wookiee.myService2.MyService2Grpc.MyService2
import com.oracle.infy.wookiee.myService2.{HelloRequest2, HelloResponse2, MyService2Grpc}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc.ServerServiceDefinition
import org.apache.curator.test.TestingServer

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ForkJoinPool, ThreadFactory}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object MultipleClients {

  // wookiee-grpc is written using functional concepts. One key concept is side-effect management/referential transparency
  // We use cats-effect (https://typelevel.org/cats-effect/) internally.
  // If you want to use cats-effect, you can use the methods that return IO[_]. Otherwise, use the methods prefixed with `unsafe`.
  // When using `unsafe` methods, you are expected to handle any exceptions
  implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()

  def main(args: Array[String]): Unit = {
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

    implicit val cs: ContextShift[IO] = IO.contextShift(mainEC1)
    implicit val timer: Timer[IO] = IO.timer(mainEC1)
    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingEC1)

    val zookeeperDiscoveryPath1 = "/discovery"
    val zookeeperDiscoveryPath2 = "/example"

    // This is just to demo, use an actual Zookeeper quorum.
    val zkFake = new TestingServer()
    val connStr = zkFake.getConnectString

    val ssd: ServerServiceDefinition = MyService.bindService(
      (request: HelloRequest) => {
        println("received request on server 1")
        Future.successful(HelloResponse("Hello " ++ request.name))
      },
      mainEC1
    )

    val ssd2: ServerServiceDefinition = MyService2.bindService(
      (request: HelloRequest2) => {
        println("received request on server 2")
        Future.successful(HelloResponse2("Hello2 " ++ request.name))
      },
      mainEC2
    )

    val serverSettings1F: ServerSettings = ServerSettings(
      zookeeperQuorum = connStr,
      discoveryPath = zookeeperDiscoveryPath1,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      serverServiceDefinition = ssd,
      port = 9091,
      // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
      // If you are running this locally, its better to explicitly set the hostname
      host = IO(Host(0, "localhost", 9091, HostMetadata(0, quarantined = false))),
      bossExecutionContext = mainEC1,
      workerExecutionContext = mainEC1,
      applicationExecutionContext = mainEC1,
      zookeeperBlockingExecutionContext = blockingEC1,
      timerExecutionContext = blockingEC1,
      bossThreads = bossThreads,
      workerThreads = mainECParallelism
    )

    val serverSettingsF2: ServerSettings = ServerSettings(
      zookeeperQuorum = connStr,
      discoveryPath = zookeeperDiscoveryPath2,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      serverServiceDefinition = ssd2,
      port = 9092,
      // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
      // If you are running this locally, its better to explicitly set the hostname
      host = IO(Host(0, "localhost", 9092, HostMetadata(0, quarantined = false))),
      bossExecutionContext = mainEC2,
      workerExecutionContext = mainEC2,
      applicationExecutionContext = mainEC2,
      zookeeperBlockingExecutionContext = blockingEC2,
      timerExecutionContext = blockingEC2,
      bossThreads = bossThreads,
      workerThreads = mainECParallelism
    )

    val serverF1 = WookieeGrpcServer.start(serverSettings1F)
    val serverF2 = WookieeGrpcServer.start(serverSettingsF2)

    val wookieeGrpcChannel1 = WookieeGrpcChannel.of(
      ChannelSettings(
        zookeeperQuorum = connStr,
        serviceDiscoveryPath = zookeeperDiscoveryPath1,
        zookeeperRetryInterval = 3.seconds,
        zookeeperMaxRetries = 20,
        zookeeperBlockingExecutionContext = blockingEC1,
        eventLoopGroupExecutionContext = blockingEC1,
        channelExecutionContext = mainEC1,
        offloadExecutionContext = blockingEC1,
        eventLoopGroupExecutionContextThreads = bossThreads,
        lbPolicy = RoundRobinPolicy
      ),
      "client1"
    )

    val wookieeGrpcChannel2 = WookieeGrpcChannel.of(
      ChannelSettings(
        zookeeperQuorum = connStr,
        serviceDiscoveryPath = zookeeperDiscoveryPath2,
        zookeeperRetryInterval = 3.seconds,
        zookeeperMaxRetries = 20,
        zookeeperBlockingExecutionContext = blockingEC2,
        eventLoopGroupExecutionContext = blockingEC2,
        channelExecutionContext = mainEC2,
        offloadExecutionContext = blockingEC2,
        eventLoopGroupExecutionContextThreads = bossThreads,
        lbPolicy = RoundRobinPolicy
      ),
      "client2"
    )

    val gRPCResponseF = for {

      c1 <- wookieeGrpcChannel1
      c2 <- wookieeGrpcChannel2
      stub1 = MyServiceGrpc.stub(c1.managedChannel)
      stub2 = MyService2Grpc.stub(c2.managedChannel)
      server1 <- serverF1
      server2 <- serverF2
      resp <- IO.fromFuture(IO { stub1.greet(HelloRequest("world!")) })
      resp2 <- IO.fromFuture(IO { stub2.greet2(HelloRequest2("world!")) })
      _ <- c1.shutdown()
      _ <- c2.shutdown()
      _ <- server2.shutdown()
      _ <- server1.shutdown()
    } yield (resp, resp2)

    println(Await.result(gRPCResponseF.unsafeToFuture(), Duration.Inf))
    zkFake.close()
    ()
  }
}
