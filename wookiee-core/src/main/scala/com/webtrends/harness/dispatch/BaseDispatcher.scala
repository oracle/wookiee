package com.webtrends.harness.dispatch

import com.webtrends.harness.command.Command

import scala.concurrent.Future
import scala.reflect.ClassTag

case class Endpoint[Input <: Product : ClassTag, Output <: Any : ClassTag](
            config: DispatcherConfig[Input, Output], command: Command)

object Endpoint {
  def apply[Input <: Product : ClassTag, Output <: Any : ClassTag]
  (config: DispatcherConfig[Input, Output], name: String): Endpoint[Input, Output] = {
    val end = Endpoint(config, name)
    config.register(name)
    end
  }
}

abstract class DispatcherConfig[Input <: Product : ClassTag, Output <: Any : ClassTag] {
  def unmarshallInput(any: Any): Input
  def marshallOutput(output: Output): Array[Byte]
  def register(name: String): Future[Boolean]
}
