package com.webtrends.harness.service.test.util

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout

import scala.concurrent.Await

object ActorWaitHelper {
  // Will wait until an actor has come up before returning its ActorRef
  def awaitActor(props: Props, system: ActorSystem)(implicit timeout: Timeout): ActorRef = {
    val actor = system.actorOf(props)
    Await.result(system.actorSelection(actor.path).resolveOne(), timeout.duration)
    actor
  }
}

trait ActorWaitHelper { this: Actor =>
  // Will wait until an actor has come up before returning its ActorRef
  def awaitActor(props: Props)(implicit timeout: Timeout = Timeout(5, TimeUnit.SECONDS)): ActorRef =
    ActorWaitHelper.awaitActor(props, context.system)
}
