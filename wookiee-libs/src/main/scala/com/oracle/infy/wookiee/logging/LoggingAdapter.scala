package com.oracle.infy.wookiee.logging

import com.oracle.infy.wookiee.actor.WookieeScheduler
import org.slf4j.{Logger, LoggerFactory}

import java.util.ServiceLoader
import java.util.logging.Level
import java.util.logging.Level._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Use this trait in your class so that there is logging support
  */
trait LoggingAdapter {

  @transient
  lazy val log: Logger = LoggerFactory.getLogger(getClass)

  // Fire and forget, will also log the error if the input function throws one
  def asyncLogErrorAndForget[A](f: => A, messageOnFail: Option[String] = None, level: Level = WARNING)(
      implicit ec: ExecutionContext = WookieeScheduler.schedulerEc
  ): Unit = {
    Future {
      tryAndLogError(f, messageOnFail, level)
      ()
    }
    ()
  }

  // Will log the error if the input function throws one and return a Try
  def tryAndLogError[A](f: => A, messageOnFail: Option[String] = None, level: Level = WARNING): Try[A] = {
    val tried = Try(f)
    if (tried.isFailure) {
      val ex = tried.failed.get
      val message = messageOnFail.getOrElse(ex.getMessage)

      level match {
        case SEVERE  => log.error(message, ex)
        case INFO    => log.info(message, ex)
        case FINE    => log.info(message, ex)
        case CONFIG  => log.debug(message, ex)
        case FINER   => log.debug(message, ex)
        case FINEST  => log.trace(message, ex)
        case WARNING => log.warn(message, ex)
        case _       => log.warn(message, ex)
      }
    }
    tried
  }
}

object LoggingAdapter {

  // Call this to see where the logging classes are coming from
  def printLoggingClasses(): Unit = {
    println("Logging classpath and class information for diagnostics...")
    logClassInformation("org.slf4j.LoggerFactory")
    logClassInformation("ch.qos.logback.classic.LoggerContext")
    logClassInformation("ch.qos.logback.core.util.StatusPrinter")
    logSLF4JServiceProvider()
  }

  def logClassInformation(className: String): Unit = {
    try {
      val clazz = Class.forName(className)
      val classLoader = clazz.getClassLoader
      val location = classLoader.getResource(className.replace('.', '/') + ".class")
      println(s"Class: $className, ClassLoader: $classLoader, Location: $location")
    } catch {
      case e: ClassNotFoundException => println(s"Class $className not found: ${e.getMessage}")
      case e: Exception              => println(s"Error loading class $className: ${e.getMessage}")
    }
  }

  def logSLF4JServiceProvider(): Unit = {
    println("SLF4J Service Providers:")
    try {
      val serviceLoader = ServiceLoader.load(classOf[org.slf4j.spi.SLF4JServiceProvider])
      serviceLoader.forEach { provider =>
        println(
          s"Provider: ${provider.getClass.getName}, sl4fj max version supported: ${provider.getRequestedApiVersion}"
        )
      }
    } catch {
      case e: Exception => println(s"Error loading SLF4J Service Providers: ${e.getMessage}")
    }
  }
}
