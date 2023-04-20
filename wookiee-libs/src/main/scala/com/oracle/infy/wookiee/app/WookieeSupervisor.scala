package com.oracle.infy.wookiee.app

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.typesafe.config.Config

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext

object WookieeSupervisor extends Mediator[WookieeSupervisor]

class WookieeSupervisor(config: Config)(implicit ec: ExecutionContext) extends WookieeMonitor {
  import WookieeSupervisor._
  registerMediator(getInstanceId(config), this)
  private val healthComponents = new TrieMap[String, WookieeMonitor]()

  override val name: String = "wookiee-supervisor"

  def startSupervising(): Unit = {
    log.info("Starting to supervise critical constituents of Wookiee..")
    val wookieeCommandManager = new WookieeCommandExecutive("wookiee-commands", config)
    healthComponents.put(wookieeCommandManager.name, wookieeCommandManager)
    log.info("Wookiee now under supervision")
  }

  override def getDependentHealths: Iterable[WookieeMonitor] = healthComponents.values
}
