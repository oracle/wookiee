package com.oracle.infy.wookiee.component.discovery.command

import com.oracle.infy.wookiee.command.WookieeCommand
import org.json4s.ext.JavaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

/**
  *  Interface for discoverable commands. These objects can be passed to the registerDiscoverableCommand method
  *  in com.oracle.infy.wookiee.component.discovery.command.DiscoverableCommandHelper to be registered with the
  *  command manager and gRPC manager. One can then execute the command remotely using the executeDiscoverableCommand
  *  method in com.oracle.infy.wookiee.component.discovery.command.DiscoverableCommandExecution
  */
abstract class DiscoverableCommand[Input <: Any: TypeTag: ClassTag, +Output <: Any: TypeTag: ClassTag]
    extends WookieeCommand[Input, Output] {
  // If the default formatter can't turn your Input type into a JSON string and back, then override this method
  implicit def format: Formats = DefaultFormats ++ JavaTimeSerializers.all

  // If custom logic is needed to turn a JSON string into an input object, override this method
  def jsonToInput(inputJson: String): Input = parse(inputJson).extract[Input]
}
