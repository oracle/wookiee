package com.webtrends.harness.service.test

import akka.actor.ActorSystem
import ch.qos.logback.classic.Level
import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.component.Component
import com.webtrends.harness.service.Service
import org.specs2.mutable.SpecificationLike
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration._

trait BaseWookieeTest {
  def config:Config = ConfigFactory.empty()
  def componentMap:Option[Map[String, Class[_<:Component]]] = None
  def servicesMap:Option[Map[String, Class[_<:Service]]] = None
  def logLevel: Level = Level.INFO
  def startupWait: FiniteDuration = 15 seconds

  TestHarness(config, servicesMap, componentMap, logLevel, startupWait)
  Thread.sleep(1000)
  implicit val system: ActorSystem = TestHarness.system.get
}

trait BaseWookieeSpecTest extends BaseWookieeTest with SpecificationLike
trait BaseWookieeScalaTest extends BaseWookieeTest with WordSpecLike with MustMatchers
