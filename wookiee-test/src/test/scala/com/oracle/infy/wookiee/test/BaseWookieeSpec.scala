package com.oracle.infy.wookiee.test

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe
import com.oracle.infy.wookiee.service.Service
import com.oracle.infy.wookiee.service.messages.Ready
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

case class ListHolder(buff: StringBuffer)

class SimpleActor extends Actor {

  override def receive: Receive = {
    case lh: ListHolder =>
      lh.buff.append("-added")
      sender() ! lh
  }
}

class BaseWookieeSpec extends BaseWookieeTest with AnyWordSpecLike with Matchers with Inspectors {

  override def servicesMap: Option[Map[String, Class[_ <: Service]]] =
    Some(Map("testservice" -> classOf[TestService]))

  "Base Wookiee Test" should {
    "Start up the services" in {
      testWookiee.serviceManager.get.isInstanceOf[ActorRef] shouldBe true //scalafix:ok
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

    "do actors keep the same object?" in {
      val probe = TestProbe()
      val actor = testWookiee.system.actorOf(Props[SimpleActor]())
      val origList = ListHolder(new StringBuffer("base"))
      probe.send(actor, origList) // actor ? origList
      val outList = probe.expectMsgType[ListHolder]
      origList.buff.toString shouldBe outList.buff.toString()
    }
  }
}
