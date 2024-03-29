package com.oracle.infy.wookiee

import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.typesafe.config.Config

import scala.collection.concurrent.TrieMap
import scala.reflect.ClassTag
import scala.util.Try

object Mediator extends LoggingAdapter {

  // Helper method to get the `instance-id` from the config
  def getInstanceId(config: Config): String =
    try {
      Try(config.getString("instance-id")).getOrElse(config.getString("wookiee-system.instance-id"))
    } catch {
      case e: Exception =>
        log.error(s"Missing 'instance-id' from top level of config, please set it to any string")
        throw e
    }
}

trait Mediator[T] extends LoggingAdapter {
  private val mediatorMap = TrieMap[String, T]()

  // Convenience method to get a mediator using config with 'instance-id' on it
  def getMediator(config: Config)(implicit classTag: ClassTag[T]): T =
    getMediator(getInstanceId(config))

  // @throws IllegalStateException when the mediator hasn't been registered yet
  def getMediator(instanceId: String)(implicit classTag: ClassTag[T]): T =
    mediatorMap.get(instanceId) match {
      case Some(instance) => instance
      case None =>
        throw new IllegalStateException(
          s"No mediator of type [${classTag.runtimeClass.getSimpleName}] for instance [$instanceId]"
        )
    }

  // Useful when polling for the mediator to be registered
  def maybeGetMediator(config: Config): Option[T] = maybeGetMediator(getInstanceId(config))

  def maybeGetMediator(instanceId: String): Option[T] = mediatorMap.get(instanceId)

  // If not present, register the mediator using the 'create' function
  def getOrCreateMediator(config: Config, create: => T)(implicit classTag: ClassTag[T]): T =
    getOrCreateMediator(Mediator.getInstanceId(config), create)

  def getOrCreateMediator(instanceId: String, create: => T)(implicit classTag: ClassTag[T]): T =
    mediatorMap.synchronized {
      mediatorMap.get(instanceId) match {
        case Some(mediator) => mediator
        case None           => registerMediator(instanceId, create)
      }
    }

  // In most cases will be called only once on startup for each class
  def registerMediator(instanceId: String, mediator: T)(implicit classTag: ClassTag[T]): T = {
    log.info(s"Registering mediator of type [${classTag.runtimeClass.getSimpleName}] for instance [$instanceId]")
    mediatorMap.put(instanceId, mediator)
    mediator
  }

  // Convenience method to register a mediator using config with 'instance-id' on it
  def registerMediator(config: Config, mediator: T)(implicit classTag: ClassTag[T]): T =
    registerMediator(getInstanceId(config), mediator)

  // Optional closing logic can be passed in to process any additional needed cleanup
  def unregisterMediator(instanceId: String, closingLogic: T => Unit = { _ =>
    ()
  })(implicit classTag: ClassTag[T]): Unit =
    if (mediatorMap.contains(instanceId)) {
      log.info(s"Unregistering mediator of type [${classTag.runtimeClass.getSimpleName}] for instance [$instanceId]")
      mediatorMap.remove(instanceId).foreach(closingLogic)
    }

  // Convenience method to unregister a mediator using config with 'instance-id' on it
  def unregisterMediator(config: Config)(implicit classTag: ClassTag[T]): Unit =
    unregisterMediator(getInstanceId(config))

  // Helper method to get the `instance-id` from the config
  def getInstanceId(config: Config): String = Mediator.getInstanceId(config)
}
