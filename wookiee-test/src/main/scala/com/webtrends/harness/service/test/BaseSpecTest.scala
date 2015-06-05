package com.webtrends.harness.service.test

import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import com.webtrends.harness.component.Component
import com.webtrends.harness.service.Service
import org.specs2.mutable.SpecificationLike

/**
 * @author Michael Cuthbert on 5/28/15.
 */
trait BaseSpecTest extends SpecificationLike {
  implicit val config:Config
  implicit val componentMap:Option[Map[String, Class[_<:Component]]] = None
  implicit val servicesMap:Option[Map[String, Class[_<:Service]]] = None

  TestHarness(config, servicesMap, componentMap, Level.ALL)
  Thread.sleep(1000)
  implicit val system = TestHarness.system.get
}
