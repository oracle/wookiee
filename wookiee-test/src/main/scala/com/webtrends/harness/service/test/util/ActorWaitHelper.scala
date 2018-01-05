package com.webtrends.harness.service.test.util

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout

import scala.concurrent.Await

object ActorWaitHelper {
  // Will wait until an actor has come up before returning its ActorRef
  def awaitActor(props: Props, system: ActorSystem, actorName: Option[String] = None)(implicit timeout: Timeout = Timeout(5, TimeUnit.SECONDS)): ActorRef = {
    val actor = actorName match {
      case Some(name) => system.actorOf(props, name)
      case None => system.actorOf(props)
    }
    awaitActorRef(actor, system)
  }

  // Will wait until an actor has come up before returning its ActorRef
  def awaitActorRef(actor: ActorRef, system: ActorSystem)(implicit timeout: Timeout = Timeout(5, TimeUnit.SECONDS)): ActorRef = {
    Await.result(system.actorSelection(actor.path).resolveOne(), timeout.duration)
    actor
  }
}

trait ActorWaitHelper { this: Actor =>
  // Will wait until an actor has come up before returning its ActorRef
  def awaitActor(props: Props)(implicit timeout: Timeout = Timeout(5, TimeUnit.SECONDS)): ActorRef =
    ActorWaitHelper.awaitActor(props, context.system)(timeout)
}
