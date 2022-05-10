package com.oracle.infy.wookiee.component.grpc

import akka.testkit.TestProbe
import com.oracle.infy.wookiee.component.Component
import com.oracle.infy.wookiee.component.grpc.GrpcManager.{CleanCheck, CleanResponse}
import com.oracle.infy.wookiee.component.grpc.utils.TestModels
import com.oracle.infy.wookiee.service.Service
import com.oracle.infy.wookiee.test.{BaseWookieeTest, TestHarness, TestService}
import com.typesafe.config.Config
import org.apache.curator.test.TestingServer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcManagerSpec extends BaseWookieeTest with AnyWordSpecLike with Matchers {
  val zkPort: Int = TestHarness.getFreePort
  val zkServer = new TestingServer(zkPort)

  override def config: Config = TestModels.conf(zkPort)

  override def servicesMap: Option[Map[String, Class[_ <: Service]]] = Some(Map(
    "testservice" -> classOf[TestService]
  ))

  override def componentMap: Option[Map[String, Class[_ <: Component]]] = Some(Map(
    "wookiee-grpc-component" -> classOf[GrpcManager]
  ))

  "gRPC Manager" should {
    "load up fully" in {
      val probe = TestProbe()
      val testComp = testWookiee.getComponent("wookiee-grpc-component")
      assert(testComp.isDefined, "gRPC Manager wasn't registered")

      probe.send(testComp.get, CleanCheck())
      CleanResponse(false) shouldEqual probe.expectMsgType[CleanResponse]
    }

    "register a simple gRPC service" in {

    }
  }
}
