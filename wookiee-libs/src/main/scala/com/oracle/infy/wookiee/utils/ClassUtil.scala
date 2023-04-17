package com.oracle.infy.wookiee.utils

import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror

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
}
