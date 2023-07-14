package com.oracle.infy.wookiee.component.grpc

import cats.effect.unsafe.implicits.global
import com.google.protobuf.StringValue
import com.oracle.infy.wookiee.component.WookieeComponent
import com.oracle.infy.wookiee.component.grpc.GrpcManager.{CleanCheck, CleanResponse, GrpcDefinition}
import com.oracle.infy.wookiee.component.grpc.utils.TestModels
import com.oracle.infy.wookiee.component.grpc.utils.TestModels._
import com.oracle.infy.wookiee.health.ComponentState.ComponentState
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent}
import com.oracle.infy.wookiee.service.Service
import com.oracle.infy.wookiee.service.messages.CheckHealth
import com.oracle.infy.wookiee.test.{BaseWookieeTest, TestHarness, TestService}
import com.typesafe.config.Config
import org.apache.curator.test.TestingServer
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Assertion, BeforeAndAfterAll}

import java.util.concurrent.atomic.AtomicReference

class GrpcManagerFaultSpec
    extends BaseWookieeTest
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {
  lazy val zkPort: Int = TestHarness.getFreePort
  lazy val grpcPort: Int = TestHarness.getFreePort
  lazy val zkServer: AtomicReference[TestingServer] = new AtomicReference(new TestingServer(zkPort))

  override def config: Config = TestModels.conf(zkPort, grpcPort)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    zkServer.get().start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    println("Shutting down Wookiee")
    testWookiee.stop()
    zkServer.get().stop()
  }

  override def servicesMap: Option[Map[String, Class[_ <: Service]]] =
    Some(
      Map(
        "test-fault-service" -> classOf[TestService]
      )
    )

  override def componentMap: Option[Map[String, Class[_ <: WookieeComponent]]] =
    Some(
      Map(
        "wookiee-grpc-component" -> classOf[GrpcManager]
      )
    )

  "gRPC Manager" should {
    "load up fully" in zkPort.synchronized {
      val testComp = testWookiee.getComponentV2("wookiee-grpc-component")
      assert(testComp.isDefined, "gRPC Manager wasn't registered")

      testComp.foreach(
        tc =>
          whenReady(tc ? CleanCheck()) {
            case resp: CleanResponse =>
              resp.clean shouldEqual false
            case _ => fail("gRPC Manager didn't respond with a CleanResponse")
          }
      )
    }

    "recover from zk going down during registration" in zkPort.synchronized {
      GrpcManager.registerGrpcService(
        system,
        "manager-spec",
        List(
          new GrpcDefinition(new GrpcServiceOne().bindService())
        )
      )
      println("Stopping zk server")
      zkServer.get().stop()
      GrpcManager.initializeGrpcNow(system)
      checkHealth(ComponentState.CRITICAL)

      println("Starting zk server again")
      zkServer.set(new TestingServer(zkPort))
      zkServer.get().start()
      GrpcManager.waitForManager(system, waitForClean = true, 150)

      val channel = GrpcManager.createChannel("/grpc/local_dev", s"localhost:$zkPort", "")
      try {
        val stub = new GrpcMockStub(channel.managedChannel)
        val resultOne = stub.sayHello(StringValue.of("msg1"), classOf[GrpcServiceOne].getSimpleName)

        resultOne.getValue shouldEqual "msg1:GrpcServiceOne"
        checkHealth(ComponentState.NORMAL)
      } finally channel.shutdown(true).unsafeRunSync()
    }
  }

  def checkHealth(expectedState: ComponentState): Assertion = {
    println("Checking health of grpc component")
    val testComp = testWookiee.getComponentV2("wookiee-grpc-component")
    assert(testComp.isDefined, "gRPC Manager wasn't registered")
    testComp
      .map(
        tc =>
          whenReady(tc ? CheckHealth, PatienceConfiguration.Timeout(Span(15, Seconds))) {
            case health: HealthComponent =>
              HealthComponent(GrpcManager.ComponentName, expectedState, health.details) shouldEqual health
            case _ => fail("gRPC Manager didn't respond with a CleanResponse")
          }
      )
      .getOrElse(fail("gRPC Manager wasn't registered"))

  }
}
