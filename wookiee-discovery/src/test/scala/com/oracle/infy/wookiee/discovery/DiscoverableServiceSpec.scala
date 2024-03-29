package com.oracle.infy.wookiee.discovery

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.oracle.infy.wookiee.component.{ComponentInfoV2, ComponentState}
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommandHelper.getZKConnectConfig
import com.oracle.infy.wookiee.discovery.command.{DiscoverableCommandExecution, WookieeDiscoverableService}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicBoolean
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
      val service = WookieeActor.actorOf(new TestDiscoverableService(ConfigFactory.empty()))
      service.onComponentReady(ComponentInfoV2(GrpcManager.ComponentName, ComponentState.Started, null))
      DiscoverableServiceSpec.calledCommands.get() mustBe true
    }

    "get config from the right place" in {
      val conf = ConfigFactory.parseString("zookeeper-config.connect-string = \"localhost:2181\"")
      getZKConnectConfig(conf).getOrElse("") mustBe "localhost:2181"
    }
  }
}
