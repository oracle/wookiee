package com.oracle.infy.wookiee.health

import akka.actor.ActorSystem
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.service.messages.CheckHealth
import com.typesafe.config.Config

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
