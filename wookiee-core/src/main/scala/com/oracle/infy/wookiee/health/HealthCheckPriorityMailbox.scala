package com.oracle.infy.wookiee.health

import akka.actor.ActorSystem
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import com.oracle.infy.wookiee.service.messages.CheckHealth
import com.typesafe.config.Config

/**
  * This is the default mailbox for Wookiee's akka actor system. All messages will be
  * routed through here to be assigned priority. This is currently being used to
  * ensure the health check messages skip to the front of the queue to prevent timeouts
  */
class HealthCheckPriorityMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedPriorityMailbox(
      // Lower value means more important
      PriorityGenerator {
        // Stop message
        case CheckHealth => 1
        // Other messages
        case _ => 2
      }
    )
