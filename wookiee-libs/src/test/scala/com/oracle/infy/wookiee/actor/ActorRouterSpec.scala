package com.oracle.infy.wookiee.actor

import com.oracle.infy.wookiee.actor.WookieeActor.Receive
import com.oracle.infy.wookiee.health.ComponentState
import com.oracle.infy.wookiee.utils.ThreadUtil
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

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
      Await.result(router.checkHealth, 5.seconds).state mustEqual ComponentState.NORMAL
      1.to(5).foreach(_ => router ! "test")
      ThreadUtil.awaitEvent(seenMessages.forall(_ == true))
    }

    "initialize and receive requests" in {
      val actorNum = new AtomicInteger(0)
      val seenMessages = Array.fill(5)(false)
      val router = WookieeActor.withRouter(new WookieeActor {
        val thisActorNum: Int = actorNum.getAndIncrement()

        override def receive: Receive = super.receive orElse {
          case _ =>
            sender() ! thisActorNum
        }
      })
      1.to(5).foreach(_ => (router ? "test").mapTo[Int].foreach(i => seenMessages(i) = true))
      ThreadUtil.awaitEvent(seenMessages.forall(_ == true))
    }

    "have high performance compared to a single actor" in {
      val iters = 50000
      val seenMessages = new AtomicInteger(0)
      def actorMaker(): WookieeActor = new WookieeActor {
        override def receive: Receive = {
          case _ =>
            seenMessages.incrementAndGet()
            ()
        }
      }

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
