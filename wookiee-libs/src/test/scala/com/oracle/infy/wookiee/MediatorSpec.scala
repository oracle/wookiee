package com.oracle.infy.wookiee

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

object TestMediator extends Mediator[String]

class MediatorSpec extends AnyWordSpec with Matchers {
  "Mediator" should {
    "get instance id from config" in {
      val instanceId = TestMediator.getInstanceId(ConfigFactory.parseString("instance-id = test"))
      instanceId mustEqual "test"
    }

    "fail gracefully when instance id is missing from config" in {
      assertThrows[Exception] {
        TestMediator.getInstanceId(ConfigFactory.empty())
      }
    }

    "can register and get mediator" in {
      val mediator = "reg-test"
      val conf = ConfigFactory.parseString(s"instance-id = $mediator")
      TestMediator.registerMediator(conf, mediator)
      TestMediator.getMediator(conf) mustEqual mediator
    }

    "can get or create mediator" in {
      val mediator = "get-or-create-test"
      val conf = ConfigFactory.parseString(s"instance-id = $mediator")
      TestMediator.getOrCreateMediator(conf, mediator)
      TestMediator.getMediator(conf) mustEqual mediator
    }

    "be able to maybe get a mediator" in {
      val mediator = "maybe-get-test"
      val conf = ConfigFactory.parseString(s"instance-id = $mediator")
      TestMediator.maybeGetMediator(conf) mustEqual None
      TestMediator.getOrCreateMediator(conf, mediator)
      TestMediator.maybeGetMediator(conf) mustEqual Some(mediator)
    }

    "be able to unregister a mediator" in {
      val mediator = "unregister-test"
      val conf = ConfigFactory.parseString(s"instance-id = $mediator")
      TestMediator.getOrCreateMediator(conf, mediator)
      TestMediator.getMediator(conf) mustEqual mediator
      TestMediator.unregisterMediator(conf)
      TestMediator.maybeGetMediator(conf) mustEqual None
    }

    "fail gracefully on get when mediator isn't registered" in {
      val mediator = "fail-test"
      val conf = ConfigFactory.parseString(s"instance-id = $mediator")
      assertThrows[IllegalStateException] {
        TestMediator.getMediator(conf)
      }
    }
  }
}
