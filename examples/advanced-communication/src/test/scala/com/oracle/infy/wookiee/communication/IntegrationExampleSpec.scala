package com.oracle.infy.wookiee.communication

import com.oracle.infy.wookiee.health.HealthCheckActor
import com.oracle.infy.wookiee.kafka.testing.KafkaTestHelper
import com.oracle.infy.wookiee.test.{BaseWookieeTest, TestHarness}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._

class IntegrationExampleSpec
    extends KafkaTestHelper
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BaseWookieeTest {

  override def beforeTestWookiee(): Unit = {
    // Needed in this case to fill in the application.conf file
    System.setProperty("serviceClass", "ExternalWookieeService")
    startKafka()
  }

  override protected def afterAll(): Unit = {
    shutdown()
    stopKafka()
  }

  "External Wookiee Service" should {
    "start itself up" in {
      val hc = Await.result(HealthCheckActor.getHealthFull(testWookiee.config), 15.seconds)
      hc.components.nonEmpty mustBe true
    }
  }

  lazy val grpcPort: Int = TestHarness.getFreePort
  override def config: Config = ConfigFactory.parseString(s"""
      |kafka {
      |    port = "$kafkaPort"
      |  }
      |
      |  # We're using wookiee-zookeeper to spin up a local ZK server
      |  # for this example.  This is not required for normal operation
      |  wookiee-zookeeper {
      |    enabled = true
      |    quorum = "${zkMode.getConnectString}"
      |  }
      |
      |  # Will need these settings for wookiee-discovery to work
      |  wookiee-grpc-component {
      |    grpc {
      |      port = $grpcPort # port on which we host gRPC server
      |      zk-discovery-path = "/wookiee/test-internal" # the client will use this path to discover this gRPC service
      |      server-host-name = "localhost" # fqdn of this server
      |    }
      |  }
      |
      |  # Turns on metrics for this service, access them at localhost:8080/metrics
      |  wookiee-metrics {
      |    enabled = true
      |  }
      |
      |  example-config { # example specific config
      |    internal {
      |      zk-path = "/wookiee/test-internal"
      |      bearer-token = "wookiee"
      |    }
      |
      |    external {
      |
      |    }
      |  }
      |""".stripMargin)
}
