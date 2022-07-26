package com.oracle.infy.wookiee.component.grpc

import akka.testkit.TestProbe
import com.google.protobuf.StringValue
import com.oracle.infy.wookiee.component.Component
import com.oracle.infy.wookiee.component.grpc.GrpcManager.{CleanCheck, CleanResponse, GrpcDefinition}
import com.oracle.infy.wookiee.component.grpc.utils.TestModels
import com.oracle.infy.wookiee.component.grpc.utils.TestModels.{
  GrpcMockStub,
  GrpcServiceFour,
  GrpcServiceOne,
  GrpcServiceThree,
  GrpcServiceTwo
}
import com.oracle.infy.wookiee.service.Service
import com.oracle.infy.wookiee.test.{BaseWookieeTest, TestHarness, TestService}
import com.typesafe.config.Config
import org.apache.curator.test.TestingServer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcManagerSpec extends BaseWookieeTest with AnyWordSpecLike with Matchers {
  lazy val zkPort: Int = TestHarness.getFreePort
  lazy val grpcPort: Int = TestHarness.getFreePort
  lazy val zkServer = new TestingServer(zkPort)
  zkServer.start()

  override def config: Config = TestModels.conf(zkPort, grpcPort)

  override def servicesMap: Option[Map[String, Class[_ <: Service]]] =
    Some(
      Map(
        "testservice" -> classOf[TestService]
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

    "register a simple gRPC service" in {
      GrpcManager.registerGrpcService(
        system,
        "manager-spec",
        List(
          new GrpcDefinition(new GrpcServiceOne().bindService()),
          new GrpcDefinition(new GrpcServiceTwo().bindService()),
          new GrpcDefinition(new GrpcServiceThree().bindService())
        )
      )
      GrpcManager.initializeGrpcNow(system)
      GrpcManager.waitForManager(system, waitForClean = true)

      val channel = GrpcManager.createChannel("/grpc/local_dev", s"localhost:$zkPort", "")
      val stub = new GrpcMockStub(channel.managedChannel)
      val resultOne = stub.sayHello(StringValue.of("msg1"), classOf[GrpcServiceOne].getSimpleName)
      val resultTwo = stub.sayHello(StringValue.of("msg2"), classOf[GrpcServiceTwo].getSimpleName)
      val resultThree = stub.sayHello(StringValue.of("msg3"), classOf[GrpcServiceThree].getSimpleName)

      resultOne.getValue shouldEqual "msg1:GrpcServiceOne"
      resultTwo.getValue shouldEqual "msg2:GrpcServiceTwo"
      resultThree.getValue shouldEqual "msg3:GrpcServiceThree"
    }

    "handle a message over the default 4MB with max-message-size configured" in {
      GrpcManager.registerGrpcService(
        system,
        "message-size-spec",
        List(
          new GrpcDefinition(new GrpcServiceFour().bindService())
        )
      )
      GrpcManager.initializeGrpcNow(system)
      GrpcManager.waitForManager(system, waitForClean = true)

      val arraySize = 8000000 // ~8MB sized message
      val arrayBig = new Array[Char](arraySize)
      val stringBig = new String(arrayBig)

      val channel = GrpcManager.createChannel("/grpc/local_dev", s"localhost:$zkPort", "", None, 10000000)
      val stub = new GrpcMockStub(channel.managedChannel)
      val resultBig = stub.sayHello(StringValue.of(stringBig), classOf[GrpcServiceFour].getSimpleName)

      resultBig.getValue shouldEqual s"$stringBig:GrpcServiceFour"
    }
  }
}
