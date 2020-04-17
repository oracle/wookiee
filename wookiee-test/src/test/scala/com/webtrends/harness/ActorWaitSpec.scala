package com.webtrends.harness

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.webtrends.harness.utils.ActorWaitHelper
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class WaitedOnActor extends Actor with ActorWaitHelper {
  def receive: Receive = {
    case "message" => sender ! "waitedResponse"
  }
}

class WaitActor extends Actor with ActorWaitHelper {
  implicit val timeout: Timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  val waited: ActorRef = awaitActor(Props[WaitedOnActor])

  def receive: Receive = {
    case "message" => sender ! "response"
    case "waited" => sender ! Await.result((waited ? "message").mapTo[String], Duration(5, "seconds"))
  }
}

class ActorWaitSpec extends TestKit(ActorSystem("wait-spec")) with WordSpecLike with Matchers with Inspectors {
  implicit val timeout: Timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  val waitActor: ActorRef = ActorWaitHelper.awaitActor(Props[WaitActor], system)

  "ActorWaitSpec" should {
    "await the WaitActor successfully " in {
      Await.result((waitActor ? "message").mapTo[String], Duration(5, "seconds"))should be ("response")
    }

    "the WaitActor's awaited actor must have come up " in {
      Await.result((waitActor ? "waited").mapTo[String], Duration(5, "seconds")) should be ("waitedResponse")
    }
  }

  "end" in  {
    waitActor ! PoisonPill
  }
}
