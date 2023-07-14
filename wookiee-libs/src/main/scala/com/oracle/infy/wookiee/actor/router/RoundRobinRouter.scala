package com.oracle.infy.wookiee.actor.router
import com.oracle.infy.wookiee.actor.WookieeActor

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

// Create this through the WookieeActor.withRouter method
// This router will route messages to the routees in a round robin fashion
class RoundRobinRouter(routees: Int) extends WookieeActorRouter {
  assert(routees > 0 && routees <= 1000, "Routees must be greater than 0 and less than or equal to 1000")
  protected var routeeActors: Array[WookieeActor] = Array.empty
  protected val currentRoutee: AtomicInteger = new AtomicInteger(0)

  override def initialize(actorMaker: => WookieeActor): Unit =
    routeeActors = Array.fill(routees)(actorMaker)

  override def !(message: Any)(implicit sender: WookieeActor = null): Unit = {
    val i = currentRoutee.getAndUpdate(old => (old + 1) % routees)
    routeeActors(i).!(message)(sender)
  }

  override def ?(message: Any)(implicit sender: WookieeActor = null): Future[Any] = {
    val i = currentRoutee.getAndUpdate(old => (old + 1) % routees)
    routeeActors(i).?(message)(sender)
  }
}
