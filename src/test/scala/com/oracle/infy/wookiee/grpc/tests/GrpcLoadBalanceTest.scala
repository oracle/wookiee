package com.oracle.infy.wookiee.grpc.tests

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO}
import cats.implicits.{catsSyntaxEq => _}
import com.oracle.infy.wookiee.grpc.common.{ConstableCommon, UTestScalaCheck}
import com.oracle.infy.wookiee.grpc.json.HostSerde
import com.oracle.infy.wookiee.grpc.settings.{ChannelSettings, ServerSettings}
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer}
import com.oracle.infy.wookiee.model.LoadBalancers.RoundRobinWeightedPolicy
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import com.oracle.infy.wookiee.utils.implicits._
import fs2.concurrent.Queue
import io.grpc.ServerServiceDefinition
import org.apache.curator.framework.CuratorFramework
import utest.{Tests, test}

import java.util.Random
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object GrpcLoadBalanceTest extends UTestScalaCheck with ConstableCommon {

  def loadBalancerTest(
      blockingEC: ExecutionContext,
      connStr: String,
      mainECParallelism: Int,
      curator: CuratorFramework
  )(
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

      // Queues for the server load info. Initialize with numbers 0-5 for testing purposes.
      val queue = {
        for {
          queue <- Queue.unbounded[IO, Int]
          _ = Seq.from(0 to 5).foreach(f => queue.enqueue1(f))
        } yield queue
      }

      val queue2 = {
        for {
          queue2 <- Queue.unbounded[IO, Int]
          _ = Seq.from(0 to 5).foreach(f => queue2.enqueue1(f))
        } yield queue2
      }

      // Hosts for the servers that will be generated later.
//      val host1 = Host(0, "localhost", 8080, Map[String, String](("load", "0"), ("quarantined", "false")))
//      val host2 = Host(0, "localhost", 9090, Map[String, String](("load", "0")))
//      val host3 = Host(0, "localhost", 9091, Map[String, String](("load", "0")))
      val host1 = Host(0, "localhost", 8080, HostMetadata(0, false))
      val host2 = Host(0, "localhost", 9090, HostMetadata(0, false))
      val host3 = Host(0, "localhost", 9091, HostMetadata(0, false))

      // Create first server. Use server settings object to initialize.
      val serverSettings: ServerSettings = ServerSettings(
        zookeeperQuorum = connStr,
        discoveryPath = zookeeperDiscoveryPath,
        zookeeperRetryInterval = 3.seconds,
        serverServiceDefinition = ssd,
        port = 8080,
        host = IO(host1),
        bossExecutionContext = blockingEC,
        workerExecutionContext = mainEC,
        applicationExecutionContext = mainEC,
        zookeeperBlockingExecutionContext = blockingEC,
        timerExecutionContext = timerEC,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism,
        queue = queue,
        quarantined = Ref.of[IO, Boolean](false)
      )

      val serverF: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(serverSettings)

      // Create a second server.
      val serverSettings2: ServerSettings = ServerSettings(
        zookeeperQuorum = connStr,
        discoveryPath = zookeeperDiscoveryPath,
        zookeeperRetryInterval = 3.seconds,
        serverServiceDefinition = ssd2,
        port = 9090,
        host = IO(host2),
        bossExecutionContext = blockingEC,
        workerExecutionContext = mainEC,
        applicationExecutionContext = mainEC,
        zookeeperBlockingExecutionContext = blockingEC,
        timerExecutionContext = timerEC,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism,
        queue = queue2,
        quarantined = Ref.of[IO, Boolean](false)
      )

      val serverF2: Future[WookieeGrpcServer] = WookieeGrpcServer.startUnsafe(serverSettings2)

      val wookieeGrpcChannel: WookieeGrpcChannel = WookieeGrpcChannel.unsafeOf(
        ChannelSettings(
          zookeeperQuorum = connStr,
          serviceDiscoveryPath = zookeeperDiscoveryPath,
          zookeeperRetryInterval = 1.seconds,
          zookeeperMaxRetries = 2,
          zookeeperBlockingExecutionContext = blockingEC,
          eventLoopGroupExecutionContext = blockingEC,
          channelExecutionContext = mainEC,
          offloadExecutionContext = blockingEC,
          eventLoopGroupExecutionContextThreads = bossThreads,
          lbPolicy = RoundRobinWeightedPolicy
        ),
        mainExecutionContext = mainEC,
        blockingExecutionContext = blockingEC,
      )

      val stub: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(wookieeGrpcChannel.managedChannel)

      // Verifies that the correct server handled the request at least 95% of the time.
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

      // Verify that the last server was used at least some of the time. (Since it is spun up later, we do not expect
      // that it will immediately handle all requests perfectly.
      def verifyLastServerIsUsed(quarantined: Boolean): Future[Boolean] = {
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
                  sameValCase <- Future(load1 === 0 || load2 === 0)
                } yield res || (sameValCase && !quarantined)
              }
          )
          .map(_.map(a => if (a) 1 else 0).sum)
          .map(_ > finish * 0.8)
      }

      // Verify that the load associated with this host in zookeeper matches what we expect.
      def verifyLoad(
          load: Int,
          host: Host,
          discoveryPath: String,
          curator: CuratorFramework
      ): Boolean = {
        val data: Either[HostSerde.HostSerdeError, Host] =
          HostSerde.deserialize(curator.getData.forPath(s"$discoveryPath/${host.address}:${host.port}"))
        data match {
          case Left(_)     => false
          case Right(host) => host.metadata.load === load
        }
      }

      val gRPCResponseF: Future[Boolean] = for {
        _ <- Future(curator.start())
        server <- serverF
        server2 <- serverF2
        _ <- server.unsafeAssignLoad(load1)
        _ <- server2.unsafeAssignLoad(load2)
        // If hello request resolves to 1 ("Hello1") then server1 was given the load.
        result <- verifyResponseHandledCorrectly()
        // Spin up a third server with load 0, and verify that it is used at least once.
        result2 <- for {
          serverSettings3: ServerSettings <- Future(
            ServerSettings(
              zookeeperQuorum = connStr,
              discoveryPath = zookeeperDiscoveryPath,
              zookeeperRetryInterval = 3.seconds,
              serverServiceDefinition = ssd3,
              zookeeperMaxRetries = 20,
              port = 9091,
              host = IO(host3),
              bossExecutionContext = blockingEC,
              workerExecutionContext = mainEC,
              applicationExecutionContext = mainEC,
              zookeeperBlockingExecutionContext = blockingEC,
              timerExecutionContext = timerEC,
              bossThreads = bossThreads,
              workerThreads = mainECParallelism
            )
          )
          // Spin up a third server with load set to 0. Verify that the server is used to handle some requests.
          server3: WookieeGrpcServer <- WookieeGrpcServer.startUnsafe(serverSettings3)
          res2 <- verifyLastServerIsUsed(quarantined = false)
          _ <- server3
            .enterQuarantine()
            .unsafeToFuture()
          res3 <- verifyLastServerIsUsed(quarantined = true)
          _ <- server3
            .exitQuarantine()
            .unsafeToFuture()
          res4 <- verifyLastServerIsUsed(quarantined = false)
          _ <- server3.shutdownUnsafe()
        } yield res2 && !res3 && res4
        load1Result = verifyLoad(load1, host1, zookeeperDiscoveryPath, curator)
        load2Result = verifyLoad(load2, host2, zookeeperDiscoveryPath, curator)
        _ <- server2.shutdownUnsafe()
        _ <- server.shutdownUnsafe()
        _ <- wookieeGrpcChannel.shutdownUnsafe()
        _ <- Future(curator.close())
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
