package com.webtrends.harness.service.test.command

import com.webtrends.harness.command.Command

import scala.concurrent.Future

case class TestPayload(name: String)

class TestCommand extends Command[TestPayload, String] {
  override def execute(bean: TestPayload): Future[String] = {
    Future.successful(s"Test ${bean.name} OK")
  }
}

object TestCommand {
  def CommandName:String = "TestCommand"
}
