package com.oracle.infy.wookiee.service

import com.typesafe.config.Config

abstract case class ServiceV2(config: Config) extends WookieeService
