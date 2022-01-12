package com.oracle.infy.wookiee.component.zookeeper

import com.oracle.infy.wookiee.component.zookeeper.NodeRegistration.getBasePath
import com.oracle.infy.wookiee.test.config.TestConfig
import com.typesafe.config.Config
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class NodeRegistrationSpec extends AnyWordSpecLike with Matchers {
  "NodeRegistration" should {
    "get base path from config" in {
      val bp = getBasePath(testConf)
      bp shouldBe "/base-path/tdc_tp/1.1"
    }

    "get base path from cluster config" in {
      val bp = getBasePath(testConf.withFallback(TestConfig.conf("wookiee-cluster.base-path = /cluster-path")))
      bp shouldBe "/cluster-path/tdc_tp/1.1"
    }
  }

  def testConf: Config = {
    TestConfig.conf("""
        |wookiee-zookeeper {
        | quorum = "fake"
        | base-path = "/base-path"
        | datacenter = "tdc"
        | pod = "tp"
        |}
      """.stripMargin)
  }
}
