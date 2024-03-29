package com.oracle.infy.wookiee.actor.router

import com.oracle.infy.wookiee.actor.WookieeActor

// Router that emulates multiple actor routing for WookieeActor
// Implementations of this like RoundRobinRouter will mainly override the !, ?, and getHealth methods
// Should be instantiated via the WookieeActor.withRouter method
trait WookieeActorRouter extends WookieeActor {
  def commandName: String
}
