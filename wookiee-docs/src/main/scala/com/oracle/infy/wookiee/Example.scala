package com.oracle.infy.wookiee

import java.util.concurrent.{Executors, TimeUnit}

import cats.effect.IO
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer}
import com.oracle.infy.wookiee.model.Host
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.grpc.ServerServiceDefinition

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object Example {

  def main(args: Array[String]): Unit = {
    val bossThreads = 2
    val mainECThreads = 4

//    val dispatcherEC = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
//    val dispatcherECDuplicate = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
//    val dispatcherEC = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
//    val blockingEC = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
    val blockingExecutorService = Executors.newCachedThreadPool()
    val mainExecutorService =  Executors.newFixedThreadPool(mainECThreads)

    val blockingEC = ExecutionContext.fromExecutor(blockingExecutorService)
    implicit val mainEC: ExecutionContext = ExecutionContext.fromExecutor(mainExecutorService)
//    val dispatcherEC = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(mainECThreads))

//    val dispatcherEC = ExecutionContext.global
//    val blockingEC = ExecutionContext.global
//    implicit val mainEC: ExecutionContext = ExecutionContext.global

    val zookeeperDiscoveryPath = "/discovery"

    // This is just to demo, use an actual Zookeeper quorum.
    //    val zkFake = new TestingServer()
    //    val connStr = zkFake.getConnectString
    val connStr = "localhost:2181"

    val ssd: ServerServiceDefinition = MyService.bindService(new MyService {
      override def greet(request: HelloRequest): Future[HelloResponse] =
        Future.successful(HelloResponse(s"Hello ${request.name}"))
    }, mainEC)

    val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(
      zookeeperQuorum = connStr,
      discoveryPath = zookeeperDiscoveryPath,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      serverServiceDefinition = ssd,
      port = 9091,
      // This is an optional arg. wookiee-grpc will try to resolve the address automatically.
      // If you are running this locally, its better to explicitly set the hostname
      localhost = Host(0, "localhost", 9091, Map.empty),
      mainExecutionContext = mainEC,
      blockingExecutionContext = blockingEC,
      bossThreads = bossThreads,
      mainExecutionContextThreads = mainECThreads
    )

    val channel = WookieeGrpcChannel.unsafeOf(
      zookeeperQuorum = connStr,
      serviceDiscoveryPath = zookeeperDiscoveryPath,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      grpcChannelThreadLimit = bossThreads,
      mainExecutionContext = mainEC,
      blockingExecutionContext = blockingEC
    )

    val stub: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(
      channel
    )

    val logger = Slf4jLogger.create[IO].unsafeRunSync()

    logger.info("########### logging work").unsafeRunSync()
    val x = for {
      server <- serverF
      _ <- Future.successful(logger.info(s"Calling greet").unsafeRunSync())
      resp <- stub.greet(HelloRequest("world!"))
      _ <- Future(channel.shutdown())
      _ <- Future(channel.awaitTermination(10, TimeUnit.HOURS))
      _ <- Future.successful(logger.info(s"Got back response $resp. Shutting down server...").unsafeRunSync())
      _ <- server.shutdownUnsafe()
      _ <- Future.successful(logger.info(s"Server was shutdown").unsafeRunSync())
    } yield resp

    println(Await.result(x, Duration.Inf))
    blockingExecutorService.shutdownNow()
    mainExecutorService.shutdownNow()
    ()
  }
}
