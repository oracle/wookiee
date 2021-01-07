package com.oracle.infy.wookiee
// NOTE: Do not use string interpolation in this example file because mdoc will fail on `$` char
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ForkJoinPool, ThreadFactory}

import cats.effect.{Blocker, ContextShift, IO, Timer}
import com.oracle.infy.wookiee.grpc.settings.{ChannelSettings, ServerSettings}
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer, WookieeGrpcUtils}
import com.oracle.infy.wookiee.model.LoadBalancers.RoundRobinPolicy
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import io.chrisdavenport.log4cats.Logger
// This is from ScalaPB generated code
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc.ServerServiceDefinition
import org.apache.curator.test.TestingServer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object Example {

  def main(args: Array[String]): Unit = {
    val bossThreads = 10
    val mainECParallelism = 10

    // wookiee-grpc is written using functional concepts. One key concept is side-effect management/referential transparency
    // We use cats-effect (https://typelevel.org/cats-effect/) internally.
    // If you want to use cats-effect, you can use the methods that return IO[_]. Otherwise, use the methods prefixed with `unsafe`.
    // When using `unsafe` methods, you are expected to handle any exceptions

    val uncaughtExceptionHandler = new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        System.err.println("Got an uncaught exception on thread " ++ t.getName ++ " " ++ e.toString)
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

    // Use a separate execution context for the timer
    val timerEC = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

    implicit val cs: ContextShift[IO] = IO.contextShift(mainEC)
    implicit val blocker: Blocker = Blocker.liftExecutionContext(blockingEC)
    implicit val timer: Timer[IO] = IO.timer(timerEC)
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()

    val zookeeperDiscoveryPath = "/discovery"

    // This is just to demo, use an actual Zookeeper quorum.
    val zkFake = new TestingServer()
    val connStr = zkFake.getConnectString

    val curator = WookieeGrpcUtils.createCurator(connStr, 5.seconds, blockingEC).unsafeRunSync()
    curator.start()

    val ssd: ServerServiceDefinition = MyService.bindService(
      (request: HelloRequest) => {
        println("received request")
        Future.successful(HelloResponse("Hello " ++ request.name))
      },
      mainEC
    )

    val serverSettingsF: ServerSettings = ServerSettings(
      discoveryPath = zookeeperDiscoveryPath,
      serverServiceDefinition = ssd,
      // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
      // If you are running this locally, its better to explicitly set the hostname
      host = Host(0, "localhost", 9091, HostMetadata(0, quarantined = false)),
      bossExecutionContext = mainEC,
      workerExecutionContext = mainEC,
      applicationExecutionContext = mainEC,
      bossThreads = bossThreads,
      workerThreads = mainECParallelism,
      curatorFramework = curator
    )

    val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.start(serverSettingsF).unsafeToFuture()

    val wookieeGrpcChannel: WookieeGrpcChannel = WookieeGrpcChannel.of(
      ChannelSettings(
        serviceDiscoveryPath = zookeeperDiscoveryPath,
        eventLoopGroupExecutionContext = blockingEC,
        channelExecutionContext = mainEC,
        offloadExecutionContext = blockingEC,
        eventLoopGroupExecutionContextThreads = bossThreads,
        lbPolicy = RoundRobinPolicy,
        curatorFramework = curator
      )
    ).unsafeRunSync()

    val stub: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(wookieeGrpcChannel.managedChannel)

    val gRPCResponseF: Future[HelloResponse] = for {
      server <- serverF
      resp <- stub.greet(HelloRequest("world!"))
      _ <- wookieeGrpcChannel.shutdown().unsafeToFuture()
      _ <- server.shutdown().unsafeToFuture()
    } yield resp

    println(Await.result(gRPCResponseF, Duration.Inf))
    curator.close()
    zkFake.close()
    ()
  }
}
