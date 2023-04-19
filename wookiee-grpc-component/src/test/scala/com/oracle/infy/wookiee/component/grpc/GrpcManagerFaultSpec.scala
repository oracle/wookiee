package com.oracle.infy.wookiee.component.grpc

import akka.testkit.TestProbe
import cats.effect.unsafe.implicits.global
import com.google.protobuf.StringValue
import com.oracle.infy.wookiee.component.Component
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
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.duration._

import java.util.concurrent.atomic.AtomicReference

class GrpcManagerFaultSpec extends BaseWookieeTest with AnyWordSpecLike with Matchers with BeforeAndAfterAll {
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

  override def componentMap: Option[Map[String, Class[_ <: Component]]] =
    Some(
      Map(
        "wookiee-grpc-component" -> classOf[GrpcManager]
      )
    )

  "gRPC Manager" should {
    "load up fully" in {
      val probe = TestProbe()
      val testComp = testWookiee.getComponent("wookiee-grpc-component")
      assert(testComp.isDefined, "gRPC Manager wasn't registered")

      testComp.foreach(comp => probe.send(comp, CleanCheck()))
      CleanResponse(false) shouldEqual probe.expectMsgType[CleanResponse]
    }

    "recover from zk going down during registration" in {
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
      Thread.sleep(3000L) // scalafix:ok
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
    val probe = TestProbe()
    val testComp = testWookiee.getComponent("wookiee-grpc-component")
    assert(testComp.isDefined, "gRPC Manager wasn't registered")
    testComp.foreach(comp => probe.send(comp, CheckHealth))
    val health = probe.expectMsgType[HealthComponent](25.seconds)
    HealthComponent(GrpcManager.ComponentName, expectedState, health.details) shouldEqual health
  }
}
