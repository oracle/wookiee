package com.oracle.infy.qa

import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.service.messages.GetMetaDetails
import com.webtrends.harness.service.meta.ServiceMetaDetails
import com.webtrends.harness.service.test.{BaseWookieeScalaTest, TestHarness}

class BasicServiceTest extends BaseWookieeScalaTest {
  override def config = ConfigFactory.empty()
  override def servicesMap = Some(Map("base" -> classOf[BasicService]))

  "BasicService" should {
    "start itself up" in {
      val probe = TestProbe()
      val testService = TestHarness.harness.get.getService("base")
      assert(testService.isDefined, "Basic Service was not registered")

      probe.send(testService.get, GetMetaDetails)
      ServiceMetaDetails(false) mustEqual probe.expectMsg(ServiceMetaDetails(false))
    }
  }
}
