package com.oracle.infy.wookiee.component

import com.oracle.infy.wookiee.command.WookieeCommandHelper
import com.typesafe.config.Config

case class ComponentV2(override val name: String, override val config: Config)
    extends WookieeComponent
    with WookieeCommandHelper
