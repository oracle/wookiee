package com.oracle.infy.wookiee.component

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.oracle.infy.wookiee.app.HarnessActor._
import com.oracle.infy.wookiee.app.HarnessClassLoader
import com.oracle.infy.wookiee.component.TestComponentV2._
import com.oracle.infy.wookiee.health.{HealthComponent, WookieeMonitor, ComponentState => CState}
import com.oracle.infy.wookiee.service.messages.CheckHealth
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object TestComponentV2 {
  val wasStarted: AtomicBoolean = new AtomicBoolean(false)
  val oneSawTwo: AtomicBoolean = new AtomicBoolean(false)
  val twoSawOne: AtomicBoolean = new AtomicBoolean(false)
  val systemWasReady: AtomicBoolean = new AtomicBoolean(false)
  val wasShutdown: AtomicBoolean = new AtomicBoolean(false)
  val innerSystemReady: AtomicBoolean = new AtomicBoolean(false)
  val innerShutdown: AtomicBoolean = new AtomicBoolean(false)
  val innerStart: AtomicBoolean = new AtomicBoolean(false)
  val innerCompReady: AtomicBoolean = new AtomicBoolean(false)
  val gotMessage: ListBuffer[String] = new ListBuffer[String]()
}

class TestComponentV2(name: String, config: Config) extends ComponentV2(name, config) {

  class HealthTest extends WookieeMonitor {

    override def getHealth: Future[HealthComponent] =
      Future.successful(HealthComponent(name, CState.DEGRADED, "test-detail-inner"))

    override def start(): Unit = innerStart.set(true)

    override def systemReady(): Unit = innerSystemReady.set(true)

    override def onComponentReady(info: ComponentInfo): Unit = innerCompReady.set(true)

    override def prepareForShutdown(): Unit = innerShutdown.set(true)

    override val name: String = "test-child"
  }

  override def onComponentReady(info: ComponentInfo): Unit =
    if (info.name == "component-v2") {
      TestComponentV2.twoSawOne.set(true)
    } else if (info.name == "component-v2-copy") {
      TestComponentV2.oneSawTwo.set(true)
    }

  override def getDependents: Iterable[WookieeMonitor] = List(new HealthTest)

  override def getHealth: Future[HealthComponent] =
    Future.successful(HealthComponent(name, CState.NORMAL, "test-detail"))

  override def start(): Unit =
    wasStarted.set(true)

  override def systemReady(): Unit =
    systemWasReady.set(true)

  override def prepareForShutdown(): Unit =
    wasShutdown.set(true)

  override def onRequest[T](msg: T): Any =
    Future.successful("request-reply")

  override def onMessage[T](msg: T): Unit =
    gotMessage.append(msg.toString)
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
    |component-v2-copy {
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
    componentManager ! LoadComponent("component-v2-copy", "com.oracle.infy.wookiee.component.TestComponentV2")
    componentManager ! InitializeComponents

    "be able to start a component" in {
      ThreadUtil.awaitResult({ if (wasStarted.get()) Some(true) else None }) mustBe true
      ThreadUtil.awaitResult({ if (innerStart.get()) Some(true) else None }) mustBe true
    }

    "be able to get health of that component" in {
      val health = (componentManager ? CheckHealth).mapTo[HealthComponent]
      whenReady(health, PatienceConfiguration.Timeout(Span(15, Seconds))) { result =>
        result.components must contain theSameElementsAs List(
          HealthComponent(
            "component-v2",
            CState.NORMAL,
            "test-detail",
            components = List(HealthComponent("test-child", CState.DEGRADED, "test-detail-inner"))
          ),
          HealthComponent(
            "component-v2-copy",
            CState.NORMAL,
            "test-detail",
            components = List(HealthComponent("test-child", CState.DEGRADED, "test-detail-inner"))
          )
        )
      }
    }

    "be alerted when another component has come up" in {
      val resOne = ThreadUtil.awaitResult({
        if (TestComponentV2.oneSawTwo.get()) Some(true) else None
      })
      resOne mustBe true
      val resTwo = ThreadUtil.awaitResult({
        if (TestComponentV2.twoSawOne.get()) Some(true) else None
      })
      resTwo mustBe true
      ThreadUtil.awaitResult({ if (innerCompReady.get()) Some(true) else None }) mustBe true
    }

    "be alerted when the system is ready" in {
      componentManager ! SystemReady
      val res = ThreadUtil.awaitResult({
        if (TestComponentV2.systemWasReady.get()) Some(true) else None
      })
      res mustBe true
      ThreadUtil.awaitResult({ if (innerSystemReady.get()) Some(true) else None }) mustBe true
    }

    "be alerted when the system is shutting down" in {
      componentManager ! PrepareForShutdown
      val res = ThreadUtil.awaitResult({
        if (TestComponentV2.wasShutdown.get()) Some(true) else None
      })
      res mustBe true
      ThreadUtil.awaitResult({ if (innerShutdown.get()) Some(true) else None }) mustBe true
    }

    "receive a message" in {
      componentManager ! Message("component-v2", ComponentMessage("test"))
      ThreadUtil.awaitResult({
        if (gotMessage.last.equals("test")) Some(true) else None
      }) mustBe true
    }

    "all components receive a broadcast" in {
      componentManager ! Broadcast(ComponentMessage(s"broad"))
      ThreadUtil.awaitEvent(gotMessage.takeRight(2) == List("broad", "broad"))
    }

    "receive a request" in {
      val reply = Await.result(
        (componentManager ? Request("component-v2", ComponentRequest("test"))).mapTo[ComponentResponse[String]],
        15.seconds
      )
      reply.resp mustEqual "request-reply"
    }
  }
}
