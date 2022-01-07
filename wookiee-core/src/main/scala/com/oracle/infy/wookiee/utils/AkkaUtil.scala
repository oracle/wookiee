package com.oracle.infy.wookiee.utils

import akka.actor.{ActorContext, ActorRef, Props}
import akka.routing.{FromConfig, RoundRobinPool}
import org.slf4j.{Logger, LoggerFactory}

/**
  * @author Michael Cuthbert on 1/28/15.
  */
object AkkaUtil {

  val externalLogger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
    * This function helps a user create a actor based on Config from the system configuration using
    * akka.actor.deployment {
    * /[ACTOR_PATH]/[ACTOR_NAME] {
    * router = round-robin
    * nr-of-instances = 5
    * }
    * }
    * This is based on the Akka FromConfig function and functionality around actor routees. This function
    * is useful because it checks to see if configuration is available for the routees, and if not if defaults
    * to using Round-Robin with 3 routees.
    *
    * @param props         The props for the actor class, using defined like Props[ActorClass] or Props(clazz, construtor_arg1, ...)
    * @param actorName     The name that you wish the actor to be
    * @param defNumRoutees DEFAULT=3 If config not found, then will default to the number of routees you put in
    * @param context       this is the implicit context that would be usually defined by the actor that you are calling this function from
    * @return
    */
  def initActorFromConfig(props: Props, actorName: String, defNumRoutees: Int = 3)(
      implicit context: ActorContext
  ): ActorRef = {
    val config = context.system.settings.config
    val deployPath = s"akka.actor.deployment.${context.self.path.toStringWithoutAddress}/$actorName"
    if (!config.hasPath(deployPath)) {
      // message primarily for debugging so that you can see immediately if your actor found the config
      externalLogger.debug(
        s"Could not find deployment config for path [$deployPath], deploying default round robin with $defNumRoutees routees"
      )
      context.actorOf(RoundRobinPool(defNumRoutees).props(props), actorName)
    } else {
      context.actorOf(FromConfig.props(props), actorName)
    }
  }
}
