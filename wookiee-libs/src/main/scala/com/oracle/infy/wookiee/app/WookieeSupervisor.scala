package com.oracle.infy.wookiee.app

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.health.WookieeHealth
import com.typesafe.config.Config

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext

object WookieeSupervisor extends Mediator[WookieeSupervisor]

class WookieeSupervisor(config: Config)(implicit ec: ExecutionContext) extends WookieeHealth with WookieeShutdown {
  import WookieeSupervisor._
  registerMediator(getInstanceId(config), this)
  private val healthComponents = new TrieMap[String, WookieeHealth]()

  override val name: String = "wookiee-supervisor"

  def startSupervising(): Unit = {
    log.info("Starting to supervise critical constituents of Wookiee..")
    val wookieeCommandManager = new WookieeCommandExecutive("wookiee-commands", config)
    healthComponents.put(wookieeCommandManager.name, wookieeCommandManager)
    log.info("Wookiee now under supervision")
  }

  override def getDependentHealths: Iterable[WookieeHealth] = healthComponents.values

  override def prepareForShutdown(): Unit =
    getDependentHealths.foreach { comp =>
      try {
        comp.prepareForShutdown()
      } catch {
        case ex: Throwable =>
          log.error(s"Entity [${comp.name}] failed to prepare for shutdown", ex)
      }
    }
}
