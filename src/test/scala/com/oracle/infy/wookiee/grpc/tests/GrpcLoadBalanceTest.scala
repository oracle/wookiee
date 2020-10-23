package com.oracle.infy.wookiee.grpc.tests

import java.util.Random

import cats.implicits.{catsSyntaxEq => _}
import com.oracle.infy.wookiee.grpc.common.UTestScalaCheck
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer}
import com.oracle.infy.wookiee.model.Host
import com.oracle.infy.wookiee.model.LoadBalancers.RoundRobinWeightedPolicy
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import io.grpc.ServerServiceDefinition
import utest.{Tests, test}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object GrpcLoadBalanceTest extends UTestScalaCheck {

  def loadBalancerTest(blockingEC: ExecutionContext, connStr: String, mainECParallelism: Int)(
      implicit mainEC: ExecutionContext
  ): Tests = {
    val testWeightedLoadBalancer = {
      val bossThreads = 2

      val zookeeperDiscoveryPath = "/discovery"

      val ssd: ServerServiceDefinition = MyService.bindService(
        (request: HelloRequest) => {
          Future.successful(HelloResponse("Hello1 " ++ request.name))
        },
        mainEC
      )

      val ssd2: ServerServiceDefinition = MyService.bindService(
        (request: HelloRequest) => {
          Future.successful(HelloResponse("Hello2 " ++ request.name))
        },
        mainEC
      )

      // Generate random load number for each server to verify load balancer is choosing least busy server.
      val randomLoad: Random = new Random()
      val load1 = randomLoad.nextInt(10)
      val load2 = randomLoad.nextInt(10)

      val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(
        zookeeperQuorum = connStr,
        discoveryPath = zookeeperDiscoveryPath,
        zookeeperRetryInterval = 3.seconds,
        zookeeperMaxRetries = 20,
        serverServiceDefinition = ssd,
        port = 8080,
        // Host is given a randomly generated load number: this is used to determine which server is the least busy.
        localhost = Host(0, "localhost", 8080, Map[String, String](("load", load1.toString))),
        mainExecutionContext = mainEC,
        blockingExecutionContext = blockingEC,
        bossExecutionContext = blockingEC,
        workerExecutionContext = mainEC,
        applicationExecutionContext = mainEC,
        zookeeperBlockingExecutionContext = blockingEC,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism
      )

      // Create a second server with another randomly generated load number. If load number is the same, the first
      // will be used.

      val serverF2: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(
        zookeeperQuorum = connStr,
        discoveryPath = zookeeperDiscoveryPath,
        zookeeperRetryInterval = 3.seconds,
        zookeeperMaxRetries = 20,
        serverServiceDefinition = ssd2,
        port = 9090,
        localhost = Host(0, "localhost", 9090, Map[String, String](("load", load2.toString))),
        mainExecutionContext = mainEC,
        blockingExecutionContext = blockingEC,
        bossExecutionContext = blockingEC,
        workerExecutionContext = mainEC,
        applicationExecutionContext = mainEC,
        zookeeperBlockingExecutionContext = blockingEC,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism
      )

      val _ = mainECParallelism
      val wookieeGrpcChannel: WookieeGrpcChannel = WookieeGrpcChannel.unsafeOf(
        zookeeperQuorum = connStr,
        serviceDiscoveryPath = zookeeperDiscoveryPath,
        zookeeperRetryInterval = 1.seconds,
        zookeeperMaxRetries = 2,
        mainExecutionContext = mainEC,
        blockingExecutionContext = blockingEC,
        zookeeperBlockingExecutionContext = blockingEC,
        eventLoopGroupExecutionContext = blockingEC,
        channelExecutionContext = mainEC,
        offloadExecutionContext = blockingEC,
        eventLoopGroupExecutionContextThreads = bossThreads,
        lbPolicy = RoundRobinWeightedPolicy
      )

      val stub: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(wookieeGrpcChannel.managedChannel)

      def verifyResponseHandledCorrectly(): Future[Boolean] = {
        val start = 0
        val finish = 100
        Future
          .sequence(
            (start to finish)
              .toList
              .map { _ =>
                for {
                  resp <- stub.greet(HelloRequest("world!"))
                  // If load number is equivalent, it doesn't matter which server was used.
                  // Otherwise, verify that the correct response was issued.
                  res <- Future(
                    (resp
                      .toString
                      .contains("Hello1") && load1 <= load2) || (resp.toString.contains("Hello2") && load2 <= load1)
                  )
                } yield res
              }
          )
          .map(_.map(a => if (a) 1 else 0).sum)
          .map(_ > (finish * 0.95))
      }

      val gRPCResponseF: Future[Boolean] = for {
        server <- serverF
        server2 <- serverF2
        // If hello request resolves to 1 ("Hello1") then server1 was given the load.
        // Otherwise, server2 was given the load.
        result <- verifyResponseHandledCorrectly()
        _ <- server2.shutdownUnsafe()
        _ <- server.shutdownUnsafe()
        _ <- wookieeGrpcChannel.shutdownUnsafe()
      } yield result
      gRPCResponseF
    }

    Tests {
      test("least busy server handles request") {
        testWeightedLoadBalancer.map(assert)
      }
    }
  }
}
