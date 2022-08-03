package com.oracle.infy.wookiee.test.extension

import com.oracle.infy.wookiee.component.{Component, ComponentInfo}
import com.oracle.infy.wookiee.service.Service
import com.oracle.infy.wookiee.test.BaseWookieeTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{Assertion, Inspectors}

import java.util.concurrent.ConcurrentHashMap

object ComponentStatusStorage {
  val componentsReceived = new ConcurrentHashMap[String, List[ComponentInfo]]()

  def addInfo(name: String, info: ComponentInfo): Unit = {
    Option(componentsReceived.get(name)) match {
      case Some(infos) =>
        componentsReceived.put(name, infos.+:(info))
      case None =>
        componentsReceived.put(name, List(info))
    }
    ()
  }
}

abstract class ReadyComponent(name: String) extends Component(name) {

  override def onComponentReady(info: ComponentInfo): Unit = {
    super.onComponentReady(info)
    ComponentStatusStorage.addInfo(name, info)
  }
}

class ReadyComponentOne(name: String) extends ReadyComponent(name)
class ReadyComponentTwo(name: String) extends ReadyComponent(name)
class ReadyComponentThree(name: String) extends ReadyComponent(name)

class ReadyService extends Service {

  override def onComponentReady(info: ComponentInfo): Unit = {
    super.onComponentReady(info)
    log.info(s"Service got readiness message from [${info.name}]")
    ComponentStatusStorage.addInfo("service", info)
  }

  override def preStart(): Unit = {
    super.preStart()
  }
}

class ComponentReadySpec extends BaseWookieeTest with AnyWordSpecLike with Matchers with Inspectors {

  override def servicesMap: Option[Map[String, Class[_ <: Service]]] =
    Some(Map("readyservice" -> classOf[ReadyService]))

  override def componentMap: Option[Map[String, Class[_ <: Component]]] =
    Some(
      Map(
        "ready-one" -> classOf[ReadyComponentOne],
        "ready-two" -> classOf[ReadyComponentTwo],
        "ready-three" -> classOf[ReadyComponentThree]
      )
    )

  def checkReadiness(name: String, expected: List[String]): Assertion = {
    val gotten = Option(ComponentStatusStorage.componentsReceived.get(name))
    gotten.isDefined shouldBe true
    val started = gotten.get.map(_.name)
    started.intersect(expected).size shouldBe expected.size
  }

  "Component Readiness" should {
    "send ready messages to all Components" in {
      checkReadiness("ready-one", List("ready-one", "ready-two", "ready-three"))
      checkReadiness("ready-two", List("ready-one", "ready-two", "ready-three"))
      checkReadiness("ready-three", List("ready-one", "ready-two", "ready-three"))
    }

    "send ready message to Service" in {
      val timeout = System.currentTimeMillis() + 10000L
      while (timeout > System.currentTimeMillis() &&
             Option(ComponentStatusStorage.componentsReceived.get("service")).isEmpty) Thread.sleep(300L)

      checkReadiness("service", List("ready-one", "ready-two", "ready-three"))
    }
  }
}
