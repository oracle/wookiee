package com.oracle.infy.wookiee.component.discovery

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicBoolean
import com.oracle.infy.wookiee.component.ComponentState
import com.oracle.infy.wookiee.component.discovery.command.{DiscoverableCommandExecution, WookieeDiscoverableService}
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.typesafe.config.{Config, ConfigFactory}
import com.oracle.infy.wookiee.component.ComponentInfoV2

import scala.concurrent.ExecutionContext

object DiscoverableServiceSpec {
  val calledCommands: AtomicBoolean = new AtomicBoolean(false)
}

class TestDiscoverableService(config: Config) extends WookieeDiscoverableService(config) {
  override val name: String = "Test Discoverable Service"

  override def addDiscoverableCommands(implicit conf: Config, ec: ExecutionContext): Unit = {
    DiscoverableServiceSpec.calledCommands.set(true)
  }
}

class DiscoverableServiceSpec extends AnyWordSpec with Matchers with DiscoverableCommandExecution {
  "Discoverable Wookiee Services" should {
    "add commands when grpc manager comes up" in {
      val service = new TestDiscoverableService(ConfigFactory.empty())
      service.onComponentReady(ComponentInfoV2(GrpcManager.ComponentName, ComponentState.Started, null))
      DiscoverableServiceSpec.calledCommands.get() mustBe true
    }

    "get config from the right place" in {
      val conf = ConfigFactory.parseString("zookeeper-config.connect-string = \"localhost:2181\"")
      getZKConnectConfig(conf).getOrElse("") mustBe "localhost:2181"
    }
  }
}
