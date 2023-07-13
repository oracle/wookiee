package com.oracle.infy.wookiee.service

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.config.ConfigHelperV2
import com.typesafe.config.Config

abstract case class ServiceV2(config: Config) extends WookieeService with WookieeActor with ConfigHelperV2 {
  override var renewableConfig: Config = config
}
