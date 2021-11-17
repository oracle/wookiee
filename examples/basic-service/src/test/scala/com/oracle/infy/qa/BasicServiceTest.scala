package com.oracle.infy.qa

import akka.testkit.TestProbe
import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.service.messages.GetMetaDetails
import com.webtrends.harness.service.meta.ServiceMetaDetails
import com.webtrends.harness.service.test.{BaseWookieeTest, TestHarness}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class BasicServiceTest extends BaseWookieeTest with AnyWordSpecLike with Matchers {
  override def config: Config = ConfigFactory.empty()
  override def servicesMap: Option[Map[String, Class[BasicService]]] = Some(Map("base" -> classOf[BasicService]))

  "BasicService" should {
    "start itself up" in {
      val probe = TestProbe()
      val testService = testWookiee.getService("base")
      assert(testService.isDefined, "Basic Service was not registered")

      probe.send(testService.get, GetMetaDetails)
      ServiceMetaDetails(false) shouldEqual probe.expectMsg(ServiceMetaDetails(false))
    }
  }
}
