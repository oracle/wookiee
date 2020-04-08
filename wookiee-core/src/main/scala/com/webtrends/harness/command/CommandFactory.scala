package com.webtrends.harness.command

import akka.actor.Props

import scala.concurrent.Future
import scala.reflect.ClassTag

object CommandFactory {
  def createCommand[U <: Product : ClassTag, V <: Any : ClassTag](
                                                      businessLogic: U => Future[V]
                                                    ): Props = {
    class FunctionalCommand extends Command[U, V] {
      override def execute(bean: U): Future[V] = {
        businessLogic(bean)
      }
    }
    object FunctionalCommand {
      def apply() = new FunctionalCommand()
    }
    Props({FunctionalCommand()})
  }

  def createCommand[U <: Product : ClassTag, V  <: Any : ClassTag](
     customUnmarshaller: Bean => U,
     businessLogic: U => Future[V],
     customMarshaller: V => Array[Byte]): Props = {

    class FunctionalCommand extends Command[Bean, Array[Byte]] {
      import context.dispatcher

      override def execute(bean: Bean): Future[Array[Byte]] = {
        val input = customUnmarshaller(bean)
        businessLogic(input) map customMarshaller
      }
    }
    object FunctionalCommand {
      def apply() = new FunctionalCommand()
    }
    Props({FunctionalCommand()})
  }
}
