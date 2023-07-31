package com.oracle.infy.wookiee.actor

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.oracle.infy.wookiee.utils.ThreadUtil
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class AkkaVsWookieeSpec extends AnyWordSpec with Matchers {
  "A Wookiee Actor in Comparison to an Akka Actor" should {
    "have similar message performances" in {
      val iters = 50000
      val system = ActorSystem("fight-me")

      var toReduce = iters
      lazy val akkaTimer = System.currentTimeMillis()
      lazy val wookieeTimer = System.currentTimeMillis()

      val isDone = new AtomicBoolean(false)
      val akkaActor = system.actorOf(Props(new Actor {
        override def receive: Receive = {
          case _ =>
            toReduce -= 1
            if (toReduce == 0) {
              println(s"Akka ms: ${System.currentTimeMillis() - akkaTimer}")
              isDone.set(true)
            }
        }
      }))

      val wookieeActor = new WookieeActor {
        override def receive: WookieeActor.Receive = {
          case _ =>
            toReduce -= 1
            if (toReduce == 0) {
              println(s"Wookiee ms: ${System.currentTimeMillis() - wookieeTimer}")
            }
        }
      }

      println(s"Starting Akka [$akkaTimer]")
      for (_ <- 1 to iters) {
        akkaActor ! "message"
      }

      ThreadUtil.awaitEvent({ isDone.get() })
      toReduce = iters
      println(s"Starting Wookiee [$wookieeTimer]")
      for (_ <- 1 to iters) {
        wookieeActor ! "message"
      }
      system.terminate()
    }

    "have similar request performances" in {
      val iters = 1000
      val system = ActorSystem("fight-me2")

      lazy val akkaTimer = System.currentTimeMillis()
      lazy val wookieeTimer = System.currentTimeMillis()

      val akkaActor = system.actorOf(Props(new Actor {
        override def receive: Receive = {
          case _ =>
            sender() ! "message"
        }
      }))

      val wookieeActor = new WookieeActor {
        override def receive: WookieeActor.Receive = {
          case _ =>
            sender() ! "message"
        }
      }

      println(s"Starting Akka [$akkaTimer]")
      implicit val timeout: Timeout = akka.util.Timeout(3.seconds)
      for (_ <- 1 to iters) {
        Await.result(akkaActor ? "message", 3.seconds)
      }
      println(s"Akka ms: ${System.currentTimeMillis() - akkaTimer}")

      println(s"Starting Wookiee [$wookieeTimer]")
      for (_ <- 1 to iters) {
        Await.result(wookieeActor ? "message", 3.seconds)
      }
      println(s"Wookiee ms: ${System.currentTimeMillis() - wookieeTimer}")
      system.terminate()
    }
  }
}
