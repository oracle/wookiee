package com.webtrends.harness.command.typed

import akka.actor.Actor
import akka.pattern._
import com.webtrends.harness.logging.ActorLoggingAdapter
import com.webtrends.harness.utils.FutureExtensions._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait TypedCommand[T, V] extends Actor with ActorLoggingAdapter {

  implicit def executionContext: ExecutionContext = context.dispatcher

  def commandName: String

  def receive: Receive = {
    case ExecuteTypedCommand(args) => pipe{
      val startTime = System.currentTimeMillis()
      execute(args.asInstanceOf[T]) mapAll {
        case Success(t) =>
          log.info(s"Command $commandName succeeded in ${System.currentTimeMillis() - startTime}ms")
          t
        case Failure(f) =>
          log.info(s"Command $commandName failed in ${System.currentTimeMillis() - startTime}ms")
          throw f
      }
    } to sender
  }

  def execute(args: T): Future[V]
}