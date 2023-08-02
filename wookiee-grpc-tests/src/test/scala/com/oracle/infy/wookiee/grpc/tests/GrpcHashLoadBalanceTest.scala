package com.oracle.infy.wookiee.grpc.tests

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.implicits.{catsSyntaxEq => _}
import com.oracle.infy.wookiee.grpc.common.{ConstableCommon, UTestScalaCheck}
import com.oracle.infy.wookiee.grpc.model.LoadBalancers.RoundRobinHashedPolicy
import com.oracle.infy.wookiee.grpc.model.{Host, HostMetadata}
import com.oracle.infy.wookiee.grpc.settings.{ChannelSettings, ServerSettings}
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer}
import com.oracle.infy.wookiee.myService.MyServiceGrpc.MyService
import com.oracle.infy.wookiee.myService.{HelloRequest, HelloResponse, MyServiceGrpc}
import io.grpc._
import org.apache.curator.framework.CuratorFramework
import org.scalactic.TolerantNumerics
import utest.{Tests, test}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import scala.util.hashing.MurmurHash3

object GrpcHashLoadBalanceTest extends UTestScalaCheck with ConstableCommon {

  def tests(
      blockingEC: ExecutionContext,
      mainECParallelism: Int,
      curator: CuratorFramework
  )(
      implicit mainEC: ExecutionContext,
      dispatcher: Dispatcher[IO]
  ): Tests = {
    val testHashLoadBalancer = {
      val bossThreads = 5
      val zookeeperDiscoveryPath = "/hash"

      val ssd1: ServerServiceDefinition = MyService.bindService(
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

      val port1 = getFreePort
      val port2 = getFreePort
      val port3 = getFreePort

      val host1 = Host(0, "localhost", port1, HostMetadata(0, quarantined = false))
      val host2 = Host(0, "localhost", port2, HostMetadata(0, quarantined = false))
      val host3 = Host(0, "localhost", port3, HostMetadata(0, quarantined = false))

      val serverSettings1: ServerSettings = ServerSettings(
        discoveryPath = zookeeperDiscoveryPath,
        serverServiceDefinition = ssd1,
        host = host1,
        sslServerSettings = None,
        authSettings = None,
        bossExecutionContext = blockingEC,
        workerExecutionContext = mainEC,
        applicationExecutionContext = mainEC,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism,
        curatorFramework = curator
      ).withMaxMessageSize(8194304)

      val serverF1: Future[WookieeGrpcServer] =
        dispatcher.unsafeToFuture(WookieeGrpcServer.start(serverSettings1))

      // Create a second server.
      val serverSettings2: ServerSettings = ServerSettings(
        discoveryPath = zookeeperDiscoveryPath,
        serverServiceDefinition = ssd2,
        host = host2,
        sslServerSettings = None,
        authSettings = None,
        bossExecutionContext = blockingEC,
        workerExecutionContext = mainEC,
        applicationExecutionContext = mainEC,
        bossThreads = bossThreads,
        workerThreads = mainECParallelism,
        curatorFramework = curator
      )

      val serverF2: Future[WookieeGrpcServer] =
        dispatcher.unsafeToFuture(WookieeGrpcServer.start(serverSettings2))

      val wookieeGrpcChannel: WookieeGrpcChannel = dispatcher.unsafeRunSync(
        WookieeGrpcChannel
          .of(
            ChannelSettings(
              serviceDiscoveryPath = zookeeperDiscoveryPath,
              eventLoopGroupExecutionContext = blockingEC,
              channelExecutionContext = mainEC,
              offloadExecutionContext = blockingEC,
              eventLoopGroupExecutionContextThreads = bossThreads,
              lbPolicy = RoundRobinHashedPolicy,
              curatorFramework = curator,
              sslClientSettings = None,
              clientAuthSettings = None,
              clientInterceptors = None
            )
          )
      )

      val stub: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(wookieeGrpcChannel.managedChannel)

      def hashFunc[T](accountId: String, hosts: List[T]): Option[T] = {
        hosts
          .lift(
            Math.abs(
              MurmurHash3
                .stringHash(accountId)
            ) % hosts.length
          )
      }

      def calculateHostId(host: Host): String = {
        s"${host.address}:${host.port}"
      }

      // Verifies that the correct server handled the request at least 95% of the time.
      def verifyConsistentHashing(hosts: List[Host]): Future[Boolean] = {
        val start = 0
        val finish = 100
        Future
          .sequence(
            (start to finish)
              .toList
              .map { _ =>
                val accountId = Random.nextString(10)

                for {
                  resp <- stub
                    .withInterceptors(new ClientInterceptor {
                      override def interceptCall[ReqT, RespT](
                          method: MethodDescriptor[ReqT, RespT],
                          callOptions: CallOptions,
                          next: Channel
                      ): ClientCall[ReqT, RespT] = {
                        next.newCall(
                          method,
                          callOptions.withOption(WookieeGrpcChannel.hashKeyCallOption, accountId)
                        )
                      }
                    })
                    .greet(HelloRequest("world!"))

                  hostList = hosts
                    .map(calculateHostId)
                    .sortBy(identity)

                  // Verify correct response comes from server based on the hash logic
                  res <- Future(
                    (resp.toString.contains("Hello1") && hashFunc(accountId, hostList)
                      .contains(calculateHostId(host1))) ||
                      (resp.toString.contains("Hello2") && hashFunc(accountId, hostList)
                        .contains(calculateHostId(host2))) ||
                      (resp.toString.contains("Hello3") && hashFunc(accountId, hostList)
                        .contains(calculateHostId(host3)))
                  )
                } yield res
              }
          )
          .map(_.map(a => if (a) 1 else 0).sum)
          .map(_ > (finish * 0.95))
      }

      // Verifies that the correct server handled the request at least 95% of the time.
      def verifyRoundRobin(): Future[Boolean] = {
        val start = 1
        val finish = 100
        Future
          .sequence(
            (start to finish)
              .toList
              .map { _ =>
                for {
                  resp <- stub.greet(HelloRequest("world!"))

                  // Verify correct response comes from server based on the hash logic
                  res <- Future(
                    if (resp.resp.contains("Hello1")) {
                      1
                    } else if (resp.resp.contains("Hello2")) {
                      2
                    } else {
                      3
                    }
                  )
                } yield res
              }
          )
          .map(_.groupBy(identity))
          .map { m =>
            val host1ResponseCount = m.getOrElse(1, Nil).length
            val host2ResponseCount = m.getOrElse(2, Nil).length
            val host3ResponseCount = m.getOrElse(3, Nil).length
            val eq = TolerantNumerics.tolerantDoubleEquality(0.10)

            eq.areEquivalent(host1ResponseCount / finish.toDouble, 0.33) &&
            eq.areEquivalent(host2ResponseCount / finish.toDouble, 0.33) &&
            eq.areEquivalent(host3ResponseCount / finish.toDouble, 0.33)

          }
      }

      val gRPCResponseF: Future[Boolean] = for {
        server1 <- serverF1
        server2 <- serverF2
        // If hash of accountId resolves to server 1 ("Hello1") then server1 was given the load.
        result1 <- verifyConsistentHashing(List(host1, host2))

        // Spin up a third server and verify that the hashing logic is still followed
        serverSettings3: ServerSettings = ServerSettings(
          discoveryPath = zookeeperDiscoveryPath,
          serverServiceDefinition = ssd3,
          host = host3,
          sslServerSettings = None,
          authSettings = None,
          bossExecutionContext = blockingEC,
          workerExecutionContext = mainEC,
          applicationExecutionContext = mainEC,
          bossThreads = bossThreads,
          workerThreads = mainECParallelism,
          curatorFramework = curator
        )
        server3: WookieeGrpcServer <- dispatcher.unsafeToFuture(WookieeGrpcServer.start(serverSettings3))
        _ <- dispatcher.unsafeToFuture(IO.sleep(500.millis))
        result2 <- verifyConsistentHashing(List(host1, host2, host3))
        result3 <- verifyRoundRobin()

        _ <- dispatcher.unsafeToFuture(server3.shutdown())
        _ <- dispatcher.unsafeToFuture(server2.shutdown())
        _ <- dispatcher.unsafeToFuture(server1.shutdown())

        _ <- dispatcher.unsafeToFuture(wookieeGrpcChannel.shutdown())
      } yield {
        result1 && result2 && result3
      }

      gRPCResponseF
    }

    Tests {
      test("server handles request according to hash rules") {
        testHashLoadBalancer.map(assert)
      }
    }
  }
}
