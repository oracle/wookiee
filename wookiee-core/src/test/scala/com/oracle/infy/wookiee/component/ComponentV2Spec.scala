package com.oracle.infy.wookiee.component

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.oracle.infy.wookiee.app.HarnessClassLoader
import com.oracle.infy.wookiee.component.TestComponentV2.wasStarted
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent, WookieeHealth}
import com.oracle.infy.wookiee.service.messages.CheckHealth
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.concurrent.duration._

object TestComponentV2 {
  val wasStarted: AtomicBoolean = new AtomicBoolean(false)
}

class TestComponentV2(name: String, config: Config) extends ComponentV2(name, config) {

  class HealthTest extends WookieeHealth {

    override def getHealth: Future[HealthComponent] =
      Future.successful(HealthComponent(name, ComponentState.DEGRADED, "test-detail-inner"))

    override val name: String = "test-child"
  }

  override def getDependentHealths: Iterable[WookieeHealth] = List(new HealthTest)

  override def getHealth: Future[HealthComponent] =
    Future.successful(HealthComponent(name, ComponentState.NORMAL, "test-detail"))

  override def start(): Unit =
    wasStarted.set(true)
}

class ComponentV2Spec
    extends TestKit(
      ActorSystem(
        "component-v2-spec",
        ConfigFactory.parseString("""
    |instance-id = "component-test"
    |component-v2 {
    |  enabled = true
    |  manager = "com.oracle.infy.wookiee.component.TestComponentV2"
    |}
    |""".stripMargin),
        HarnessClassLoader(Thread.currentThread.getContextClassLoader)
      )
    )
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  implicit val timeout: Timeout = Timeout(5.seconds)

  "ComponentV2" should {
    val componentManager = system.actorOf(ComponentManager.props)
    componentManager ! LoadComponent("component-v2", "com.oracle.infy.wookiee.component.TestComponentV2")

    "be able to start a component" in {
      val result = ThreadUtil.awaitResult({ if (wasStarted.get()) Some(true) else None })
      result mustBe true
    }

    "be able to get health of that component" in {
      val health = (componentManager ? CheckHealth).mapTo[HealthComponent]
      whenReady(health) { result =>
        result.components.head mustBe
          HealthComponent(
            "component-v2",
            ComponentState.NORMAL,
            "test-detail",
            components = List(HealthComponent("test-child", ComponentState.DEGRADED, "test-detail-inner"))
          )
      }
    }
  }
}
