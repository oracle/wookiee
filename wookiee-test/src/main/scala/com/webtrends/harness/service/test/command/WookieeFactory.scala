package com.webtrends.harness.service.test.command

import com.webtrends.harness.command.Command

import scala.concurrent.Future
import scala.reflect.ClassTag

object WookieeFactory {
  def createWookieeCommand[U <: Product: ClassTag, V: ClassTag](
                                                      name: String,
                                                      bodyClass: Class[U],
                                                      businessLogic: U => Future[V]
                                                    ): Class[_ <: Command[U, V]] = {
    class NewWookiee extends Command[U, V] {
      override def execute(bean: U): Future[V] = {
        businessLogic(bean)
      }
    }
    object NewWookiee {
      def apply() = new NewWookiee
    }

    classOf[NewWookiee]
  }
}
