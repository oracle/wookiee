package com.webtrends.harness

import akka.actor.ActorRef
import akka.testkit.TestProbe
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.messages.Ready
import com.webtrends.harness.service.test.{BaseWookieeTest, TestHarness, TestService}
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class BaseWookieeSpec extends BaseWookieeTest with WordSpecLike with Matchers with Inspectors {
  override def servicesMap: Option[Map[String, Class[_ <: Service]]] =
    Some(Map("testservice" -> classOf[TestService]))

  "Base Wookiee Test" should {
    "Start up the services" in {
      testWookiee.serviceManager.get.isInstanceOf[ActorRef] shouldBe true
    }

    "load both test services " in {
      def checkReady(sysToUse: TestHarness) = {
        val probe = TestProbe()
        val testService = sysToUse.getService("testservice")
        assert(testService.isDefined, "Test service was not registered")
        probe.send(testService.get, Ready)
        Ready shouldBe probe.expectMsg(Ready)
      }

      checkReady(testWookiee)
    }
  }
}
