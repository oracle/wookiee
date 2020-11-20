package com.oracle.infy.wookiee.grpc.tests

import java.util.Random

import cats.effect.{ContextShift, IO}
import cats.implicits.{catsSyntaxEq => _}
import com.oracle.infy.wookiee.grpc.common.{ConstableCommon, UTestScalaCheck}
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer}
import com.oracle.infy.wookiee.model.Host
import com.oracle.infy.wookiee.model.LoadBalancers.RoundRobinWeightedPolicy
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import fs2.concurrent.Queue
import io.grpc.ServerServiceDefinition
import utest.{Tests, test}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object GrpcLoadBalanceTest extends UTestScalaCheck with ConstableCommon {

  def loadBalancerTest(blockingEC: ExecutionContext, connStr: String, mainECParallelism: Int)(
      implicit mainEC: ExecutionContext
  ): Tests = {
    val testWeightedLoadBalancer = {
      val bossThreads = 5
      val zookeeperDiscoveryPath = "/discovery"
      implicit val cs: ContextShift[IO] = IO.contextShift(mainEC)

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

      val ssd3: ServerServiceDefinition = MyService.bindService(
        (request: HelloRequest) => {
          Future.successful(HelloResponse("Hello3 " ++ request.name))
        },
        mainEC
      )

      // Generate random load number for each server to verify load balancer is choosing least busy server.
      val randomLoad: Random = new Random()
      val load1 = randomLoad.nextInt(10)
      val load2 = randomLoad.nextInt(10)
      val timerEC = mainExecutionContext(mainECParallelism)

      val queue: Queue[IO, Int] = Queue.unbounded[IO, Int].unsafeRunSync()
      Seq.from(0 to 5).foreach(f => queue.enqueue1(f).unsafeRunSync())
      val queue2: Queue[IO, Int] = Queue.unbounded[IO, Int].unsafeRunSync()
      Seq.from(0 to 5).foreach(f => queue2.enqueue1(f).unsafeRunSync())

      val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(
        zookeeperQuorum = connStr,
        discoveryPath = zookeeperDiscoveryPath,
        zookeeperRetryInterval = 3.seconds,
        zookeeperMaxRetries = 20,
        serverServiceDefinition = ssd,
        port = 8080,
        // Host is given a randomly generated load number: this is used to determine which server is the least busy.
        localhost = Host(0, "localhost", 8080, Map[String, String](("load", "0"))),
        mainExecutionContext = mainEC,
        blockingExecutionContext = blockingEC,
        timerEC = timerEC,
        bossExecutionContext = blockingEC,
        workerExecutionContext = mainEC,
        applicationExecutionContext = mainEC,
        zookeeperBlockingExecutionContext = blockingEC,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism,
        queue
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
        localhost = Host(0, "localhost", 9090, Map[String, String](("load", "0"))),
        mainExecutionContext = mainEC,
        blockingExecutionContext = blockingEC,
        timerEC = timerEC,
        bossExecutionContext = blockingEC,
        workerExecutionContext = mainEC,
        applicationExecutionContext = mainEC,
        zookeeperBlockingExecutionContext = blockingEC,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism,
        queue = queue2
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

      def verifyLastServerIsUsed(): Future[Boolean] = {
        val start = 0
        val finish = 1000
        Future
          .sequence(
            (start to finish)
              .toList
              .map { _ =>
                for {
                  resp <- stub.greet(HelloRequest("world!"))
                  // If load1 or load2 are 0, then it doesn't matter whether they were used or server3.
                  // Otherwise, we should expect server 3 to handle the request since its load is 0.
                  res <- Future(
                    resp
                      .toString
                      .contains("Hello3")
                  )
                  sameValCase <- Future(load1 == 0 || load2 == 0)
                } yield res || sameValCase
              }
          )
          .map(_.map(a => if (a) 1 else 0).sum)
          .map(_ > 1)
      }

      val gRPCResponseF: Future[Boolean] = for {
        server <- serverF
        server2 <- serverF2
        _ <- server.unsafeAssignLoad(load1)
        _ <- server2.unsafeAssignLoad(load2)
        // If hello request resolves to 1 ("Hello1") then server1 was given the load.
        result <- verifyResponseHandledCorrectly()
        // Spin up a third server with load 0, and verify that it is used at least once.
        result2 <- for {
          server3: WookieeGrpcServer <- WookieeGrpcServer.startUnsafe(
            zookeeperQuorum = connStr,
            discoveryPath = zookeeperDiscoveryPath,
            zookeeperRetryInterval = 3.seconds,
            zookeeperMaxRetries = 20,
            serverServiceDefinition = ssd3,
            port = 9091,
            localhost = Host(0, "localhost", 9091, Map[String, String](("load", "0"))),
            mainExecutionContext = mainEC,
            blockingExecutionContext = blockingEC,
            timerEC = timerEC,
            bossExecutionContext = blockingEC,
            workerExecutionContext = mainEC,
            applicationExecutionContext = mainEC,
            zookeeperBlockingExecutionContext = blockingEC,
            bossThreads = bossThreads,
            workerThreads = mainECParallelism,
            queue
          )
          res2 <- verifyLastServerIsUsed()
          _ <- server3.shutdownUnsafe()
        } yield res2
        load1Result = server.verifyLoad(load1, "localhost", 8080, zookeeperDiscoveryPath)
        load2Result = server.verifyLoad(load2, "localhost", 9090, zookeeperDiscoveryPath)
        _ <- server2.shutdownUnsafe()
        _ <- server.shutdownUnsafe()
        _ <- wookieeGrpcChannel.shutdownUnsafe()
      } yield result && result2 && load1Result && load2Result

      gRPCResponseF
    }

    Tests {
      test("least busy server handles request") {
        testWeightedLoadBalancer.map(assert)
      }
    }
  }
}
