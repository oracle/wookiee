package com.oracle.infy.wookiee.test

import akka.actor.ActorSystem
import ch.qos.logback.classic.Level
import com.oracle.infy.wookiee.component.WookieeComponent
import com.oracle.infy.wookiee.service.WookieeService
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}

// Add 'with WordSpecLike with MustMatchers' or 'with SpecificationLike' depending on scalatest/specs2
trait BaseWookieeTest {
  def config: Config = ConfigFactory.empty()

  def componentMap: Option[Map[String, Class[_ <: WookieeComponent]]] = None

  def servicesMap: Option[Map[String, Class[_ <: WookieeService]]] = None

  def logLevel: Level = Level.INFO

  def startupWait: FiniteDuration = 20.seconds

  // Override to execute logic before we create our TestHarness,
  // good for starting up local kafka/zookeeper/etc.
  def beforeTestWookiee(): Unit = {}

  beforeTestWookiee()

  implicit lazy val testWookiee: TestHarness =
    TestHarness(config, servicesMap, componentMap, logLevel, startupWait)
  testWookiee

  Thread.sleep(1000)
  implicit lazy val system: ActorSystem = testWookiee.system
  implicit lazy val ec: ExecutionContext = ThreadUtil.createEC(s"wookiee-test-${testWookiee.getInstanceId}")

  def getWookieeInstanceId: String = testWookiee.getInstanceId
  def shutdown(): Unit = TestHarness.shutdown()
}
