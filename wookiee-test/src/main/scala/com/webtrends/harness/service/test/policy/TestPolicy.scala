package com.webtrends.harness.service.test.policy

import java.util.concurrent.TimeUnit

import com.webtrends.harness.policy.Policy
import com.webtrends.harness.service.test.command.TestCommand

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object TestPolicy extends Policy {
  def PolicyName = "TestPolicy"

  def testPolicy() : Option[String] = {
    val bean = executeCommand[String](TestCommand.CommandName)
    Some(Await.result(decomposeCommandResponse(bean),Duration(2, TimeUnit.SECONDS)))
  }

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  override def addCommands: Unit = {
    addCommand(TestCommand.CommandName, classOf[TestCommand])
    super.addCommands
  }
}