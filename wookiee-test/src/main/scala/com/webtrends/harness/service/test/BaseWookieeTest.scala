package com.webtrends.harness.service.test

import java.net.ServerSocket

import akka.actor.ActorSystem
import ch.qos.logback.classic.Level
import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.component.Component
import com.webtrends.harness.service.Service

import scala.concurrent.duration._

// Add 'with WordSpecLike with MustMatchers' or 'with SpecificationLike' depending on scalatest/specs2
trait BaseWookieeTest {
  def config: Config = ConfigFactory.empty()
  def componentMap: Option[Map[String, Class[_<:Component]]] = None
  def servicesMap: Option[Map[String, Class[_<:Service]]] = None
  def logLevel: Level = Level.INFO
  def startupWait: FiniteDuration = 15 seconds
  def port: Int = freePort

  private val freePort: Int = getFreePort

  val testWookiee: TestHarness =
    TestHarness(config, servicesMap, componentMap, logLevel, startupWait, port)

  Thread.sleep(1000)
  implicit val system: ActorSystem = TestHarness.system(port).get

  def shutdown(): Unit =
    TestHarness.shutdown(port)

  def getFreePort: Int = {
    // Get an empty port for akka http websocket
    val socket = new ServerSocket(0)
    try {
      socket.setReuseAddress(true)
      socket.getLocalPort
    } finally if (socket != null) socket.close()
  }
}
