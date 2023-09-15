package com.oracle.infy.wookiee.utils

import org.json4s._
import org.json4s.jackson.Serialization.write

import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._

object ClassUtil {

  def instantiateClass[T](clazz: Class[T], constructorArgs: Any*): T = {
    // Obtain the runtime mirror and class symbol
    val classSymbol = currentMirror.classSymbol(clazz)

    // Obtain the class's default constructor
    val defaultConstructor = classSymbol.toType.decl(termNames.CONSTRUCTOR).asMethod

    // Create an instance mirror and instantiate the class using the default constructor
    val classMirror = currentMirror.reflectClass(classSymbol)
    val constructorMirror = classMirror.reflectConstructor(defaultConstructor)

    constructorMirror(constructorArgs: _*).asInstanceOf[T]
  }

  def getSimpleNameSafe(clazz: Class[_]): String = {
    try {
      clazz.getSimpleName
    } catch {
      // If the case of lambda functions or anonymous classes, the simple name is not available
      case _: Throwable =>
        val fullName = clazz.getName
        val lastDotIndex = fullName.lastIndexOf('.')
        val lastDollarIndex = fullName.lastIndexOf('$')
        val startIndex = Math.max(lastDotIndex, lastDollarIndex) + 1
        fullName.substring(startIndex)
    }
  }

  // Used to call Serialization.write(..) on a value of type Any (usually only accepts AnyRef)
  def writeAny(value: Any)(implicit formats: Formats): String = value match {
    case x: Int     => write(x: java.lang.Integer)
    case x: Double  => write(x: java.lang.Double)
    case x: Float   => write(x: java.lang.Float)
    case x: Long    => write(x: java.lang.Long)
    case x: Short   => write(x: java.lang.Short)
    case x: Byte    => write(x: java.lang.Byte)
    case x: Char    => write(x: java.lang.Character)
    case x: Boolean => write(x: java.lang.Boolean)
    case _: Unit    => write("")
    case x: AnyRef  => write(x)
    case _          => throw new IllegalArgumentException("Unsupported value type")
  }
}
