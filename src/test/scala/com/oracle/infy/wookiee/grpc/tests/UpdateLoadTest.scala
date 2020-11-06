package com.oracle.infy.wookiee.grpc.tests

import java.util.Random

import cats.effect.{ConcurrentEffect, ContextShift, IO}
import com.oracle.infy.wookiee.grpc.ZookeeperUtils._
import com.oracle.infy.wookiee.grpc.common.ConstableCommon
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer}
import com.oracle.infy.wookiee.model.Host
import com.oracle.infy.wookiee.model.LoadBalancers.RoundRobinWeightedPolicy
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import fs2.concurrent.Queue
import io.grpc.ServerServiceDefinition

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object UpdateLoadTest extends ConstableCommon {

  def main(args: Array[String]): Unit = {
    val _ = args
    val randomLoad: Random = new Random()
    val load = randomLoad.nextInt(10)
    val mainECParallelism = 100
    implicit val ec: ExecutionContext = mainExecutionContext(mainECParallelism)
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    implicit val concurrent: ConcurrentEffect[IO] = IO.ioConcurrentEffect
    val blockingEC: ExecutionContext = blockingExecutionContext("integration-test")

    val connStr = "localhost:2181"
    val discoveryPath = "/example"
    createDiscoveryPath(connStr, discoveryPath)

    val ssd: ServerServiceDefinition = MyService.bindService(
      (request: HelloRequest) => {
        Future.successful(HelloResponse("Hello " ++ request.name))
      },
      ec
    )
    val queue = Queue.unbounded[IO, Int].unsafeRunSync()
    val start = 0
    val finish = 10
    Future
      .sequence((start to finish).map(queue.enqueue1(_).unsafeToFuture()))
    val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(
      zookeeperQuorum = connStr,
      discoveryPath = discoveryPath,
      zookeeperRetryInterval = 3.seconds,
      zookeeperMaxRetries = 20,
      serverServiceDefinition = ssd,
      port = 8080,
      // Host is given a randomly generated load number: this is used to determine which server is the least busy.
      localhost = Host(0, "localhost", 2181, Map[String, String](("load", load.toString))),
      mainExecutionContext = ec,
      blockingExecutionContext = blockingEC,
      bossThreads = 2,
      mainExecutionContextThreads = mainECParallelism,
      queue = queue
    )
    val wookieeGrpcChannel: WookieeGrpcChannel = WookieeGrpcChannel.unsafeOf(
      zookeeperQuorum = connStr,
      serviceDiscoveryPath = discoveryPath,
      zookeeperRetryInterval = 1.seconds,
      zookeeperMaxRetries = 2,
      grpcChannelThreadLimit = 3,
      lbPolicy = RoundRobinWeightedPolicy,
      mainExecutionContext = ec,
      blockingExecutionContext = blockingEC
    )
    val _: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(wookieeGrpcChannel.managedChannel)

    val res = {
      for {
        server <- serverF
        _ <- Future(server.assignLoad(load + 2))
        _ <- server.shutdownUnsafe()
      } yield ()
    }
    println(Await.result(res, Duration.Inf))
    ()
  }
}
