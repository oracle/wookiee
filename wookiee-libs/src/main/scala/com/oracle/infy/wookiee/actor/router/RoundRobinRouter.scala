package com.oracle.infy.wookiee.actor.router
import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.actor.WookieeActor.PoisonPill
import com.oracle.infy.wookiee.command.WookieeCommand
import com.oracle.infy.wookiee.health.HealthComponent
import com.oracle.infy.wookiee.service.messages.CheckHealth

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

// Create this through the WookieeActor.withRouter method
// This router will route messages to the routees in a round robin fashion
class RoundRobinRouter(routees: Int, actorMaker: => WookieeActor) extends WookieeActorRouter {
  assert(routees > 0 && routees <= 1000, "Routees must be greater than 0 and less than or equal to 1000")

  protected val innerCommandName: AtomicReference[String] = new AtomicReference[String]("<not-set>")

  protected val routeeActors: Array[WookieeActor] = 0
    .until(routees)
    .map { _ =>
      val actor = WookieeActor.actorOf(actorMaker)
      actor match {
        case command: WookieeCommand[_, _] =>
          innerCommandName.set(command.commandName)
        case _ =>
          innerCommandName.set(actor.name)
      }
      actor
    }
    .toArray
  protected val currentRoutee: AtomicInteger = new AtomicInteger(0)

  override def commandName: String = innerCommandName.get()

  override def !(message: Any)(implicit sender: WookieeActor = noSender): Unit = message match {
    case _: PoisonPill =>
      stopAll()
    case PoisonPill =>
      stopAll()
    case msg =>
      val i = currentRoutee.getAndUpdate(old => (old + 1) % routees)
      routeeActors(i).!(msg)(sender)
  }

  def stopAll(): Unit = routeeActors.foreach(_ ! PoisonPill)

  override def ?(
      message: Any
  )(implicit timeout: FiniteDuration = 60.seconds, sender: WookieeActor = noSender): Future[Any] = {
    val i = currentRoutee.getAndUpdate(old => (old + 1) % routees)
    routeeActors(i).?(message)(timeout, sender)
  }

  // Asks a random routee for its health
  override protected def getHealth: Future[HealthComponent] =
    ?(CheckHealth).mapTo[HealthComponent]
}
