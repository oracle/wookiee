package com.oracle.infy.wookiee.service

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.app.HarnessActor._
import com.oracle.infy.wookiee.app.HarnessClassLoader
import com.oracle.infy.wookiee.component.{ComponentInfo, ComponentState => CState, _}
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent, WookieeMonitor}
import com.oracle.infy.wookiee.service.TestServiceV2._
import com.oracle.infy.wookiee.service.messages.CheckHealth
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.concurrent.duration._

object TestServiceV2 {
  val wasStarted: AtomicBoolean = new AtomicBoolean(false)
  val sawComponent: AtomicBoolean = new AtomicBoolean(false)
  val systemWasReady: AtomicBoolean = new AtomicBoolean(false)
  val wasShutdown: AtomicBoolean = new AtomicBoolean(false)
}

class TestServiceV2(config: Config) extends ServiceV2(config) {

  class HealthTest extends WookieeMonitor {

    override def getHealth: Future[HealthComponent] =
      Future.successful(HealthComponent(name, ComponentState.DEGRADED, "test-detail-inner"))

    override val name: String = "test-child"
  }

  override def onComponentReady(info: ComponentInfo): Unit =
    if (info.name == "component-v2")
      sawComponent.set(true)

  override def getDependents: Iterable[WookieeMonitor] = List(new HealthTest)

  override def getHealth: Future[HealthComponent] =
    Future.successful(HealthComponent(name, ComponentState.NORMAL, "test-detail"))

  override def start(): Unit =
    wasStarted.set(true)

  override def systemReady(): Unit =
    systemWasReady.set(true)

  override def prepareForShutdown(): Unit =
    wasShutdown.set(true)
}

class ServiceV2Spec
    extends TestKit(
      ActorSystem(
        "service-v2-spec",
        ConfigFactory.parseString("""
    |instance-id = "service-test"
    |services {
    |  internal = "com.oracle.infy.wookiee.service.TestServiceV2"
    |}
    |""".stripMargin),
        HarnessClassLoader(Thread.currentThread.getContextClassLoader)
      )
    )
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  implicit val timeout: Timeout = Timeout(5.seconds)

  "ServiceV2" should {
    val serviceManager = system.actorOf(ServiceManager.props)

    "be able to start a service" in {
      val result = ThreadUtil.awaitResult({ if (wasStarted.get()) Some(true) else None })
      result mustBe true
    }

    "be able to get health of that service" in {
      val health = (serviceManager ? CheckHealth).mapTo[HealthComponent]
      // Override the
      whenReady(health, PatienceConfiguration.Timeout(Span(15, Seconds))) { result =>
        result.components must contain theSameElementsAs List(
          HealthComponent(
            "TestServiceV2",
            ComponentState.NORMAL,
            "test-detail",
            components = List(HealthComponent("test-child", ComponentState.DEGRADED, "test-detail-inner"))
          )
        )
      }
    }

    "be alerted when another component has come up" in {
      serviceManager ! ComponentReady(
        ComponentInfoV2(
          "component-v2",
          CState.Started,
          WookieeActor.actorOf(new TestComponentV2("component-v2", system.settings.config))
        )
      )

      val resOne = ThreadUtil.awaitResult({
        if (TestServiceV2.sawComponent.get()) Some(true) else None
      })
      resOne mustBe true
    }

    "be alerted when the system is ready" in {
      serviceManager ! SystemReady
      val res = ThreadUtil.awaitResult({
        if (TestServiceV2.systemWasReady.get()) Some(true) else None
      })
      res mustBe true
    }

    "be alerted when the system is shutting down" in {
      serviceManager ! PrepareForShutdown
      val res = ThreadUtil.awaitResult({
        if (TestServiceV2.wasShutdown.get()) Some(true) else None
      })
      res mustBe true
    }
  }
}
