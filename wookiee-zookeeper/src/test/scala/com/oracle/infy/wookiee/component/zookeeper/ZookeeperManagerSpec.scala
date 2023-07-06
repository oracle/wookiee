package com.oracle.infy.wookiee.component.zookeeper

import com.oracle.infy.wookiee.health.HealthCheckActor
import com.oracle.infy.wookiee.test.BaseWookieeTest
import com.oracle.infy.wookiee.test.TestHarness.getFreePort
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.concurrent.ScalaFutures

class ZookeeperManagerSpec extends BaseWookieeTest with AnyWordSpecLike with Matchers {

  "ZookeeperManager" should {
    "register its underlying actor" in {
      ZookeeperService.maybeGetMediator(testWookiee.getInstanceId) mustBe defined
    }

    "get health check that includes underlying actor" in {
      ScalaFutures.whenReady(HealthCheckActor.getHealthFull(testWookiee.config)) { health =>
        val compMan = health.components.find(_.name == "component-manager").get
        val zkMan = compMan.components.find(_.name == ZookeeperManager.ComponentName).get
        zkMan.components.head.name mustBe "zookeeper"
      }
    }
  }

  override def config: Config = ConfigFactory.parseString(s"""
       |wookiee-zookeeper {
       |  mock-enabled = true
       |  mock-port = $getFreePort
       |}
       |""".stripMargin).withFallback(super.config)
}
