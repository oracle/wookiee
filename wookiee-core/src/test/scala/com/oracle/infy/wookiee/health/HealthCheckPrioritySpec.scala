package com.oracle.infy.wookiee.health

import akka.actor.{Actor, ActorSystem, Props}
import com.oracle.infy.wookiee.app.{HActor, HarnessActorSystem}
import com.oracle.infy.wookiee.service.messages.CheckHealth
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

class HealthCheckPrioritySpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {
  implicit val sys: ActorSystem = ActorSystem("system", HarnessActorSystem.renewConfigsAndClasses(None))

  val iterations = 100

  val basicIncr = new AtomicInteger(0)
  val healthIncr = new AtomicInteger(0)

  val basicChecked = new AtomicBoolean(false)
  val healthChecked = new AtomicBoolean(false)

  override protected def afterAll(): Unit = {
    super.afterAll()
    sys.terminate()
    ()
  }

  case class NormalMessage()

  def healthReceive(incr: AtomicInteger, bool: AtomicBoolean): PartialFunction[Any, Unit] = {
    case CheckHealth =>
      if (incr.get < iterations) bool.set(true)
    case NormalMessage =>
      Thread.sleep(1L)
      incr.incrementAndGet()
      ()
  }

  class BasicActor() extends Actor {
    override def receive: Receive = healthReceive(basicIncr, basicChecked)
  }

  class HealthActor() extends HActor {
    override def receive: Receive = healthReceive(healthIncr, healthChecked)
  }

  "All actors" should {
    "prioritize CheckHealth over other messages" in {
      val actor = sys.actorOf(Props(new BasicActor))
      0.until(iterations).foreach { _ =>
        actor ! NormalMessage
      }
      actor ! CheckHealth

      val checked = new AtomicInteger(0)
      while (basicIncr.get() < iterations && checked.incrementAndGet() < 100) Thread.sleep(100L)

      basicChecked.get() shouldEqual true
    }

    "also work in HActor case" in {
      val actor = sys.actorOf(Props(new HealthActor))
      0.until(iterations).foreach { _ =>
        actor ! NormalMessage
      }
      actor ! CheckHealth

      val checked = new AtomicInteger(0)
      while (healthIncr.get() < iterations && checked.incrementAndGet() < 100) Thread.sleep(100L)

      healthChecked.get() shouldEqual true
    }
  }
}
