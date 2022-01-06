package com.oracle.infy.wookiee.test

import akka.actor.ActorSystem
import ch.qos.logback.classic.Level
import com.oracle.infy.wookiee.component.Component
import com.oracle.infy.wookiee.service.Service
import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

// Add 'with WordSpecLike with MustMatchers' or 'with SpecificationLike' depending on scalatest/specs2
trait BaseWookieeTest {
  def config: Config = ConfigFactory.empty()

  def componentMap: Option[Map[String, Class[_ <: Component]]] = None

  def servicesMap: Option[Map[String, Class[_ <: Service]]] = None

  def logLevel: Level = Level.INFO

  def startupWait: FiniteDuration = 15.seconds

  val testWookiee: TestHarness =
    TestHarness(config, servicesMap, componentMap, logLevel, startupWait)

  Thread.sleep(1000)
  implicit val system: ActorSystem = testWookiee.system

  def shutdown(): Unit = TestHarness.shutdown()
}
