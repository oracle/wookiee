package com.webtrends.harness.command

import akka.util.Timeout
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.test.BaseWookieeTest
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

case class FactoryInput(value: String)
case class FactoryOutput(value: String)

class FactoryService extends Service {
  override def addCommands(): Unit = {
    // Basic command
    addCommand("Basic", { input: FactoryInput =>
      Future.successful(FactoryOutput(input.value + "-output"))
    })
    // Command with marshallers
    addCommand[FactoryInput, FactoryOutput]("Marshal",
      { bean: Bean =>
        bean match {
          case mb: MapBean =>
            FactoryInput(mb.map("value").toString)
          case ab: ArrayBean =>
            FactoryInput(ab.array.head.toString)
        }
      }, { input: FactoryInput =>
        Future.successful(FactoryOutput(input.value + "-output"))
      }, { out: FactoryOutput =>
        out.value.getBytes
      })
  }
}

class CommandFactorySpec extends BaseWookieeTest with Matchers with WordSpecLike {
  implicit val timeout: Timeout = Timeout(3.seconds)
  override def servicesMap: Option[Map[String, Class[_ <: Service]]] =
    Some(Map("factory-service" -> classOf[FactoryService]))

  "Command Factory" should {
    "Add and run a new basic Command" in {
      val result = Await.result(CommandHelper
        .executeCommand[FactoryInput, FactoryOutput]("Basic", FactoryInput("basic")), timeout.duration)
      result.value shouldBe "basic-output"
    }

    "Add and run a new Marshal Command with MapBean" in {
      val result = new String(Await.result(CommandHelper
        .executeCommand[Bean, Array[Byte]]("Marshal",
          MapBean(Map("value" -> "marshal"))), timeout.duration))
      result shouldBe "marshal-output"
    }

    "Add and run a new Marshal Command with ArrayBean" in {
      val result = new String(Await.result(CommandHelper
        .executeCommand[Bean, Array[Byte]]("Marshal",
          ArrayBean(Array("marshal"))), timeout.duration))
      result shouldBe "marshal-output"
    }
  }
}


