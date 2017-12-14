package com.webtrends.harness.service.test.util

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout

import scala.concurrent.Await

object ActorWaitHelper {
  def awaitActor(props: Props, system: ActorSystem): ActorRef = {
    implicit val waitTime = Timeout(5, TimeUnit.SECONDS)
    val actor = system.actorOf(props)
    Await.result(system.actorSelection(actor.path).resolveOne(), waitTime.duration)
    actor
  }
}

trait ActorWaitHelper { this: Actor =>
  def awaitActor(props: Props): ActorRef = ActorWaitHelper.awaitActor(props, context.system)
}
