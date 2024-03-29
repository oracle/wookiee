package com.oracle.infy.wookiee.actor

import com.oracle.infy.wookiee.actor.WookieeActor._
import com.oracle.infy.wookiee.health.ComponentState.ComponentState
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent}
import com.oracle.infy.wookiee.service.messages.CheckHealth
import com.oracle.infy.wookiee.utils.ThreadUtil
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future, Promise}

class WookieeActorSpec extends AnyWordSpec with Matchers {
  "WookieeActor" must {
    "have a name and path and return itself" in {
      val actor = WookieeActor.actorOf(new WookieeActor {
        override val name: String = "wookiee-actor"
        override val path: String = self.name

        override def receive: Receive = {
          case _ =>
        }
      })
      actor.name mustBe "wookiee-actor"
      actor.path mustBe "wookiee-actor"
    }

    "can be started and shutdown" in {
      var started = false
      var stopped = false
      val actor = WookieeActor.actorOf(new WookieeActor {
        override protected def preStart(): Unit = {
          super.preStart()
          started = true
        }
        override protected def postStop(): Unit = {
          super.postStop()
          stopped = true
        }

        override def receive: Receive = super.receive orElse {
          case _ =>
        }
      })
      actor.prepareForShutdown()

      actor.path mustEqual actor.name
      ThreadUtil.awaitEvent({
        started
      })
      started mustEqual true
      stopped mustEqual true
    }

    "will call its preStart with init'd variables" in {
      @volatile
      var preStarted = false
      WookieeActor.actorOf(new WookieeActor {
        val test = "test"
        val state: ComponentState = ComponentState.NORMAL

        override protected def preStart(): Unit = {
          preStarted = test.equals("test") && state == ComponentState.NORMAL
        }
      })
      ThreadUtil.awaitEvent({
        preStarted
      })
      preStarted mustEqual true
    }

    "will call its preStart with init'd variables on existing class" in {
      var preStarted = false
      class TestWookieeActor extends WookieeActor {
        protected val test = "test"
        val state: ComponentState = ComponentState.NORMAL

        override protected def preStart(): Unit = {
          preStarted = "test".equals(test) && state == ComponentState.NORMAL
        }
      }
      WookieeActor.actorOf(new TestWookieeActor)
      ThreadUtil.awaitEvent({
        preStarted
      }, 5000L)
      preStarted mustEqual true
    }

    "fails gracefully on startup" in {
      intercept[IllegalArgumentException] {
        WookieeActor.actorOf(new WookieeActor {
          override protected def preStart(): Unit =
            throw new IllegalArgumentException("fail-on-purpose")
        })
      }
    }

    "will call its postStop on PoisonPill" in {
      var postStopped = false
      val actor = WookieeActor.actorOf(new WookieeActor {
        override protected def postStop(): Unit = {
          postStopped = true
        }
      })
      actor ! PoisonPill
      ThreadUtil.awaitEvent({
        postStopped
      })
      postStopped mustEqual true
      postStopped = false
      actor ! PoisonPill()
      ThreadUtil.awaitEvent({
        postStopped
      })
      postStopped mustEqual true
    }

    "can be sent messages" in {
      var messageReceived = false
      val actor = WookieeActor.actorOf(new WookieeActor {
        override def receive: Receive = {
          case _ =>
            messageReceived = true
            sender() ! "discarded"
        }
      })
      actor ! "test"
      ThreadUtil.awaitEvent({ messageReceived })
      messageReceived mustEqual true
    }

    var messageReceived = false
    val routingActor = WookieeActor.actorOf(new WookieeActor {
      override def receive: Receive = {
        case _ =>
          messageReceived = true
          sender() ! "reply"
      }
    })

    "can be sent messages and reply" in {
      var replyReceived = false

      implicit val actor2: WookieeActor = WookieeActor.actorOf(new WookieeActor {
        override def receive: Receive = {
          case _ => replyReceived = true
        }
      })
      routingActor ! "test"
      ThreadUtil.awaitEvent({ replyReceived })
      messageReceived mustEqual true
      replyReceived mustEqual true
    }

    "handles messages in order" in {
      val iters = 2000
      var iter = 0
      var inOrder = true
      val actor = WookieeActor.actorOf(new WookieeActor {
        override protected def receive: Receive = {
          case i: Int =>
            inOrder = inOrder && i == iter
            iter += 1
        }
      })

      // Send events and ensure they are received in order
      0.until(iters).foreach(i => actor ! i)
      ThreadUtil.awaitEvent({ iters - 1 <= iter })
      inOrder mustEqual true
    }

    "can be sent requests" in {
      val actor = WookieeActor.actorOf(new WookieeActor {
        override def receive: Receive = {
          case _ =>
            sender() ! "reply"
        }
      })
      val reply = Await.result(actor ? "test", 5.seconds)
      reply mustEqual "reply"
    }

    var gotReply = false
    val replyActor = WookieeActor.actorOf(new WookieeActor {
      override def receive: Receive = {
        case _ =>
          sender() ! "reply"
          sender() ! "reply-2"
      }
    })

    "can handle extra messages after request" in {
      implicit val receiveActor: WookieeActor = WookieeActor.actorOf(new WookieeActor {
        override protected def receive: Receive = {
          case _ =>
            gotReply = true
        }
      })

      val reply = Await.result(replyActor ? "test", 5.seconds)
      reply mustEqual "reply"
      ThreadUtil.awaitEvent({ gotReply })
    }

    "can fail gracefully during requests" in {
      val actor = WookieeActor.actorOf(new WookieeActor {
        override def receive: Receive = {
          case "pipe" =>
            pipe(Future.failed(new Exception("test")))
          case _ =>
            throw new Exception("test")
        }
      })
      intercept[Exception] {
        Await.result(actor ? "test", 5.seconds)
      }
      intercept[Exception] {
        Await.result(actor ? "pipe", 5.seconds)
      }
      actor ! "pipe"
    }

    "can time out gracefully during requests" in {
      val actor = WookieeActor.actorOf(new WookieeActor {
        override def receive: Receive = {
          case _ =>
            Thread.sleep(50000L)
        }
      })
      implicit val timeout: FiniteDuration = 100.millis
      intercept[TimeoutException] {
        Await.result(actor ? "test", 5.seconds)
      }
    }

    "can schedule single events and get them" in {
      WookieeScheduler.setThreads(32)
      var messageReceived = false
      val actor = WookieeActor.actorOf(new WookieeActor {
        override def receive: Receive = {
          case _ => messageReceived = true
        }
      })
      val timeStarted = System.currentTimeMillis()
      actor.scheduleOnce(100.millis, actor, "test")
      ThreadUtil.awaitEvent({ messageReceived })
      messageReceived mustEqual true
      System.currentTimeMillis() - timeStarted > 100 mustEqual true
    }

    "can schedule recurring events and get them" in {
      var messages = 0
      val actor = WookieeActor.actorOf(new WookieeActor {
        override def receive: Receive = {
          case _ => messages += 1
        }
      })
      actor.schedule(100.millis, 100.millis, actor, "test")
      ThreadUtil.awaitEvent({
        messages >= 3
      })
      messages >= 3 mustEqual true
    }

    "can schedule any function to execute" in {
      val actor = WookieeActor.actorOf(new WookieeActor {})
      var wasExecuted = false
      actor.scheduleOnce(10.millis)({ wasExecuted = true })
      ThreadUtil.awaitEvent({ wasExecuted })
    }

    "can return health" in {
      val actor = WookieeActor.actorOf(new WookieeActor {})
      ThreadUtil.awaitResponse[HealthComponent](actor, CheckHealth).state mustEqual ComponentState.NORMAL
    }

    "can become different states" in {
      var state = "initial"
      val actor: WookieeActor = WookieeActor.actorOf(new WookieeActor {
        override def receive: Receive = {
          case _ =>
            become(changed)
        }

        def changed: Receive = {
          case _ =>
            state = "changed"
        }
      })
      actor ! "test"
      actor ! "test"
      ThreadUtil.awaitEvent({ state == "changed" })
      state mustEqual "changed"
    }

    "stash and unstash messages" in {
      val iters = 100
      var iter = 0
      var inOrder = true
      val actor: WookieeActor = WookieeActor.actorOf(new WookieeActor {
        def process: Receive = {
          case i: Int =>
            inOrder = inOrder && i == iter
            iter += 1
        }

        override protected def receive: Receive = {
          case i: Int =>
            stash(i)
          case "unstash" =>
            become(process)
            unstashAll()
        }
      })

      // Send events and ensure they are received in order
      0.until(iters).foreach(i => actor ! i)
      actor ! "unstash"
      ThreadUtil.awaitEvent({
        iters - 1 <= iter
      })
      inOrder mustEqual true
    }

    "throws IllegalStateException when starting outside of actorOf" in {
      intercept[IllegalStateException] {
        new WookieeActor {}
      }
    }

    "unapply on interceptor" in {
      val inter = AskInterceptor(Promise[Any](), None)
      AskInterceptor.unapply(inter).isDefined mustEqual true
      val poisonPill = PoisonPill()
      PoisonPill.unapply(poisonPill) mustEqual true
    }
  }
}
