package com.webtrends.harness.command.typed

import akka.pattern._
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

case class ExecuteTypedCommand(args: Any)

object TypedCommandExecution {

  def execute[U, V](name: String, args: U)(implicit executionContext: ExecutionContext, timeout: Timeout): Future[V] = {
    TypedCommandManager.commands.get(name) match {
      case Some(commandActor) =>
        (commandActor ? ExecuteTypedCommand(args)).map(_.asInstanceOf[V])
      case None =>
        Future.failed(new IllegalArgumentException(s"Command $name not found."))
    }
  }

}
