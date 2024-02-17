package com.oracle.infy.wookiee.actor

import com.oracle.infy.wookiee.actor.WookieeActor.{PoisonPill, Receive}
import com.oracle.infy.wookiee.command.WookieeCommand
import com.oracle.infy.wookiee.health.ComponentState
import com.oracle.infy.wookiee.utils.ThreadUtil
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class ActorRouterSpec extends AnyWordSpec with Matchers {
  "Wookiee Actor Router" should {
    "initialize and receive messages" in {
      val actorNum = new AtomicInteger(0)
      val seenMessages = Array.fill(5)(false)
      val router = WookieeActor.withRouter(new WookieeActor {
        val thisActorNum: Int = actorNum.getAndIncrement()

        override def receive: Receive = super.receive orElse {
          case _ =>
            seenMessages(thisActorNum) = true
        }
      })
      router.executionContext() != null mustEqual true
      Await.result(router.checkHealth, 5.seconds).state mustEqual ComponentState.NORMAL
      1.to(5).foreach(_ => router ! "test")
      ThreadUtil.awaitEvent(seenMessages.forall(_ == true))
    }

    "initialize a command" in {
      val router = WookieeActor.withRouter(new WookieeCommand[AnyRef, AnyRef] {
        override def commandName: String = "wookiee-test-command"
        override def execute(args: AnyRef): Future[AnyRef] = ???
      })
      router.commandName mustEqual "wookiee-test-command"
    }

    "initialize and receive requests" in {
      val actorNum = new AtomicInteger(0)
      val seenMessages = Array.fill(5)(false)
      var seenStop = Array.fill(5)(false)
      val router = WookieeActor.withRouter(new WookieeActor {
        val thisActorNum: Int = actorNum.getAndIncrement()
        override val name: String = "test-actor"

        override protected def postStop(): Unit =
          seenStop(thisActorNum) = true

        override def receive: Receive = super.receive orElse {
          case _ =>
            sender() ! thisActorNum
        }
      })
      1.to(5).foreach(_ => (router ? "test").mapTo[Int].foreach(i => seenMessages(i) = true))
      ThreadUtil.awaitEvent(seenMessages.forall(_ == true))
      router.commandName mustEqual "test-actor"
      router ! PoisonPill
      ThreadUtil.awaitEvent(seenStop.forall(_ == true))
      seenStop = Array.fill(5)(false)
      router ! PoisonPill()
      ThreadUtil.awaitEvent(seenStop.forall(_ == true))
    }

    "have high performance compared to a single actor" in {
      val iters = 10000
      val seenMessages = new AtomicInteger(0)
      def actorMaker(): WookieeActor =
        WookieeActor.actorOf(new WookieeActor {
          override def receive: Receive = {
            case _ =>
              seenMessages.incrementAndGet()
              ()
          }
        })

      val router = WookieeActor.withRouter(actorMaker())

      val singleActor = actorMaker()
      val start2 = System.currentTimeMillis()
      1.to(iters).foreach(_ => singleActor ! "test")
      ThreadUtil.awaitEvent(seenMessages.get() == iters)
      val end2 = System.currentTimeMillis()
      println(s"Time taken for single: ${end2 - start2}ms")

      seenMessages.set(0)
      val start = System.currentTimeMillis()
      1.to(iters).foreach(_ => router ! "test")
      ThreadUtil.awaitEvent(seenMessages.get() == iters)
      val end = System.currentTimeMillis()
      println(s"Time taken for router: ${end - start}ms")
    }
  }
}
