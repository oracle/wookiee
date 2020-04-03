package com.webtrends.harness.command

import akka.actor.Props

import scala.concurrent.Future
import scala.reflect.ClassTag

object WookieeFactory {
  def createWookieeCommand[U <: Product : ClassTag, V <: Any : ClassTag](
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

  def createWookieeCommand[U <: Product : ClassTag, V  <: Any : ClassTag](
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
