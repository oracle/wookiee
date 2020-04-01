package com.webtrends.harness.service.test.command

import akka.actor.Props
import com.webtrends.harness.command.{Bean, Command}

import scala.concurrent.Future
import scala.reflect.ClassTag

object WookieeFactory {
  def createWookieeCommand[U <: Product: ClassTag, V: ClassTag](
                                                      bodyClass: Class[U], // TODO Where to use?
                                                      businessLogic: U => Future[V]
                                                    ): Props = {
    class NewWookiee extends Command[U, V] {
      override def execute(bean: U): Future[V] = {
        businessLogic(bean)
      }
    }
    object NewWookiee {
      def apply() = new NewWookiee()
    }
    Props({NewWookiee()})
  }

  // TODO: what about simple commands that don't return anything, do they need a custom unmarshaller?
  def createWookieeCommand[U <: Product: ClassTag, V: ClassTag](
     customUnmarshaller: Bean => U,
     businessLogic: U => Future[V],
     customMarshaller: V => Array[Byte]): Props = {

    class NewWookiee extends Command[Bean, Array[Byte]] {
      import context.dispatcher

      override def execute(bean: Bean): Future[Array[Byte]] = {
        val input = customUnmarshaller(bean)
        businessLogic(input) map customMarshaller
      }
    }
    object NewWookiee {
      def apply() = new NewWookiee()
    }
    Props({NewWookiee()})
  }
}
