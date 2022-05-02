package com.oracle.infy.wookiee
// NOTE: Do not use string interpolation in this example file because mdoc will fail on `$` char
import cats.effect.IO
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ForkJoinPool, ScheduledThreadPoolExecutor, ThreadFactory}
//wookiee-grpc imports
import com.oracle.infy.wookiee.grpc._
import com.oracle.infy.wookiee.grpc.model.LoadBalancers._
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings._
import io.grpc._
//wookiee-grpc imports
import org.typelevel.log4cats.Logger
// This is from ScalaPB generated code
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import io.grpc.ServerServiceDefinition
import org.apache.curator.test.TestingServer
import org.typelevel.log4cats.slf4j.Slf4jLogger

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
    val scheduler = new ScheduledThreadPoolExecutor(1, { r: Runnable =>
      val t = new Thread(r)
      t.setName("")
      t.setDaemon(true)
      t.setPriority(Thread.MAX_PRIORITY)
      t
    })
    scheduler.setRemoveOnCancelPolicy(true)
    implicit val runtime: IORuntime =
      IORuntime(mainEC, blockingEC, Scheduler.fromScheduledExecutor(scheduler), () => (), IORuntimeConfig())

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

    //Creating a Server
    val serverSettingsF: ServerSettings = ServerSettings(
      discoveryPath = zookeeperDiscoveryPath,
      serverServiceDefinition = ssd,
      // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
      // If you are running this locally, its better to explicitly set the hostname
      host = Host(0, "localhost", 9091, HostMetadata(0, quarantined = false)),
      authSettings = None,
      sslServerSettings = None,
      bossExecutionContext = mainEC,
      workerExecutionContext = mainEC,
      applicationExecutionContext = mainEC,
      bossThreads = bossThreads,
      workerThreads = mainECParallelism,
      curatorFramework = curator
    )

    val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.start(serverSettingsF).unsafeToFuture()
    //Creating a Server

    //channelSettings
    val wookieeGrpcChannel: WookieeGrpcChannel = WookieeGrpcChannel
      .of(
        ChannelSettings(
          serviceDiscoveryPath = zookeeperDiscoveryPath,
          eventLoopGroupExecutionContext = blockingEC,
          channelExecutionContext = mainEC,
          offloadExecutionContext = blockingEC,
          eventLoopGroupExecutionContextThreads = bossThreads,
//           Load Balancing Policy
//             One of:
//               RoundRobinPolicy
//               RoundRobinWeightedPolicy
//               RoundRobinHashedPolicy
          lbPolicy = RoundRobinPolicy,
          curatorFramework = curator,
          sslClientSettings = None,
          clientAuthSettings = None
        )
      )
      .unsafeRunSync()

    val stub: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(wookieeGrpcChannel.managedChannel)
    //channelSettings

    //grpcCall
    val gRPCResponseF: Future[HelloResponse] = for {
      server <- serverF
      resp <- stub
        .withInterceptors(new ClientInterceptor {
          override def interceptCall[ReqT, RespT](
              method: MethodDescriptor[ReqT, RespT],
              callOptions: CallOptions,
              next: Channel
          ): ClientCall[ReqT, RespT] = {
            next.newCall(
              method,
              // Set the WookieeGrpcChannel.hashKeyCallOption when using RoundRobinHashedPolicy
              callOptions.withOption(WookieeGrpcChannel.hashKeyCallOption, "Some hash")
            )
          }
        })
        .greet(HelloRequest("world!"))
      _ <- wookieeGrpcChannel.shutdown().unsafeToFuture()
      _ <- server.shutdown().unsafeToFuture()
    } yield resp

    println(Await.result(gRPCResponseF, Duration.Inf))
    curator.close()
    zkFake.close()
    ()
    //grpcCall
  }
}
