package com.oracle.infy.wookiee.test.command

import com.oracle.infy.wookiee.command.WookieeCommandExecutive._
import com.oracle.infy.wookiee.command.{WookieeCommand, WookieeCommandExecutive}
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent}
import com.oracle.infy.wookiee.test.BaseWookieeTest
import com.oracle.infy.wookiee.utils.ThreadUtil
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class WookieeCommandExecutiveSpec extends BaseWookieeTest with AnyWordSpecLike with Matchers {
  case class TestInput(value: String)
  case class TestOutput(value: String)

  case class BasicCommand(id: Int) extends WookieeCommand[TestInput, TestOutput] {
    override def commandName: String = s"basic-command-$id"

    override def execute(args: TestInput): Future[TestOutput] = {
      Future.successful(TestOutput(args.value + "-output"))
    }
  }

  "Wookiee Command Manager" should {
    val commandManager =
      ThreadUtil.awaitResult[WookieeCommandExecutive](Some(getMediator(getWookieeInstanceId)), ignoreError = true)

    "register a basic command and serve it up" in {
      commandManager.registerCommand(BasicCommand(1))
      val result = Await
        .result(
          commandManager.executeCommand[TestOutput]("basic-command-1", TestInput("basic")),
          5.seconds
        )
      result.value mustEqual "basic-output"
    }

    "get healths of all commands" in {
      commandManager.registerCommand(BasicCommand(2))
      val result = Await
        .result(
          commandManager.checkHealth,
          5.seconds
        )
      result.name mustEqual "wookiee-commands"
      result.components.size mustEqual 2
      result.components must contain allOf (
        HealthComponent("basic-command-1", ComponentState.NORMAL, "Command [basic-command-1] is healthy."),
        HealthComponent("basic-command-2", ComponentState.NORMAL, "Command [basic-command-2] is healthy.")
      )
    }
  }
}
