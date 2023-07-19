package com.oracle.infy.wookiee.test

import com.oracle.infy.wookiee.service.WookieeService
import com.oracle.infy.wookiee.service.messages.Ready
import com.oracle.infy.wookiee.service.meta.ServiceMetaDataV2
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class BaseWookieeSpec extends BaseWookieeTest with AnyWordSpecLike with Matchers with Inspectors {

  override def servicesMap: Option[Map[String, Class[_ <: WookieeService]]] =
    Some(Map("testservice" -> classOf[TestService]))

  "Base Wookiee Test" should {
    "Start up the services" in {
      testWookiee.serviceManager.isDefined shouldBe true
    }

    "load both test services " in {
      def checkReady(sysToUse: TestHarness) = {
        val testService = sysToUse.getService("testservice")
        assert(testService.isDefined, "Test service was not registered")
        val ready =
          Await.result((testService.get.asInstanceOf[ServiceMetaDataV2].service ? Ready()).mapTo[Ready], 5.seconds)
        ready shouldBe Ready()
      }

      checkReady(testWookiee)
    }
  }
}
