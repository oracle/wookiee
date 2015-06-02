/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.service

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.logging.ActorLoggingAdapter
import com.webtrends.harness.service.messages.GetMetaDetails
import com.webtrends.harness.service.meta.{ServiceMetaDetails, ServiceMetaData}
import com.webtrends.harness.app.HarnessClassLoader
import java.io.File
import java.nio.file.FileSystems
import java.util.jar.JarFile
import com.webtrends.harness.utils.ConfigUtil
import org.joda.time.DateTime
import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}
import scala.concurrent.ExecutionContext.Implicits.global

trait ServiceLoader { this: Actor with ActorLoggingAdapter =>

  val services = collection.mutable.HashMap[ServiceMetaData, (ActorSelection, Option[ServiceClassLoader])]()
  private val userDir = System.getProperty("user.dir")
  private val libDir = userDir + (if (!new File(userDir + "/lib").exists) { if (new File(userDir + "/dist").exists) "/dist" else "/target" } else "")
  private val harnessLibs = FileSystems.getDefault.getPath(libDir + "/lib").toFile.listFiles match {
    case null => null
    case files => files.filter(_.getName.endsWith("jar"))
  }

  /**
   * Load the services
   * @param context The service manager's context
   */
  def load(context: ActorContext) {
    Try({
      val internalService = ConfigUtil.getDefaultValue(HarnessConstants.KeyInternalService, context.system.settings.config.getString, "")
      if (internalService.nonEmpty) {
        val loader = Thread.currentThread.getContextClassLoader.asInstanceOf[HarnessClassLoader]
        val serviceName = internalService.split("\\.").reverse(0)
        loadService(serviceName, loader.loadClass(internalService))
      } else {
          ServiceManager.serviceDir(context.system.settings.config) match {
            case Some(s) =>
              val dirs = s.listFiles.filter(_.isDirectory).sortBy(f => f.getName)
              if (dirs.length > 0) {
                dirs.foreach(subPath => {
                  Try({
                    log.info("Loading the service(s) in {}", subPath.getPath)
                    loadClasses(context, subPath)
                  }).recover({
                    case e: Throwable =>
                      log.error(e, "Error loading the service(s) in {}", subPath.getPath)
                  })
                })
              }
              log.info("%s Services have been loaded".format(services.size))

            case None => // ignore
          }
      }
    }).recover({
      case e: Throwable => log.error("Error loading the service(s)", e)
    })
  }

  /**
   * This method will load a potential service and all of its dependent jars
   * @param context The service manager's context
   * @param rootPath The services root file path
   */
  private def loadClasses(context: ActorContext, rootPath: File) = {
    val jars = rootPath.listFiles().filter(_.getName.endsWith("jar"))
    if (jars.length > 0) {

      val libPath = rootPath.getPath.concat("/lib")
      log.info("Loading the dependent jars in {}", libPath)

      val allJars = Array.concat[File](jars, new File(libPath).listFiles()
        .filter(_.getName.endsWith("jar"))
        .filterNot(jar => harnessLibs.map(_.getName).contains(jar.getName)))

      val urls = allJars.map(_.toURI.toURL)
      val harnessLoader = Thread.currentThread.getContextClassLoader.asInstanceOf[HarnessClassLoader]

      // Load the urls into the class loader
      val loader = ConfigUtil.getDefaultValue(HarnessConstants.KeyServiceDistinctClassLoader, context.system.settings.config.getBoolean, true) match {
        case true =>
          val pcl = new ServiceClassLoader(urls, harnessLoader)
          harnessLoader.addChildLoader(pcl)
          pcl
        case false =>
          harnessLoader.addURLs(urls)
          harnessLoader
      }

      jars.foreach(file => processJar(context, rootPath, file, loader))
    }
    else {
      log.warning("No jar was found for the service in {}", rootPath)
    }
  }

  /**
   * Process the given jar and look for any services to load
   * @param context The service manager's context
   * @param rootPath The services root file path
   * @param jar The potential services jar file
   * @param loader The class loader to use
   */
  private def processJar(context: ActorContext,
                         rootPath: File,
                         jar: File,
                         loader: ClassLoader): Unit = {

    // Open the jar file
    val jarFile = new JarFile(jar)
    val loaderOpt = loader.isInstanceOf[ServiceClassLoader] match {
      case true => Some(loader.asInstanceOf[ServiceClassLoader])
      case false => None
    }

    try {
      val sClass = classOf[Service]

      val localServices = jarFile.entries().flatMap[(ServiceMetaData, (ActorSelection, Option[ServiceClassLoader]))](entry =>
        try {
          val entryName = entry.getName
          if (entryName.endsWith(".class")) {
            val clazz = loader.loadClass(entryName.replace(".class", "").replaceAll("/", "."))

            if (sClass.isAssignableFrom(clazz) && !clazz.getName.equals(sClass.getName) ) {

              val name = clazz.getSimpleName.toLowerCase
              val manifest = jarFile.getManifest
              val build = manifest.getMainAttributes.getValue("Implementation-Build") match {
                case null => "N/A"
                case s if s.nonEmpty => s
                case _ => "N/A"
              }
              val classPath = manifest.getMainAttributes.getValue("Class-Path") match {
                case null => "N/A"
                case s if s.nonEmpty => s
                case _ => "N/A"
              }
              val version = "%s.%s".format(manifest.getMainAttributes.getValue("Implementation-Version"), build)

              val serv = Try {
                val serviceActor = context.actorOf(Props(clazz.asInstanceOf[Class[_ <: Actor]]), name)

                // Watch this actor
                context watch serviceActor
                // Get the meta data
                val meta = getServiceMetaDetails(context, serviceActor)
                log.info("{}: {}",name,loader.toString)

                ServiceMetaData(name,
                  version,
                  DateTime.now(),
                  rootPath.getAbsolutePath,
                  serviceActor.path.toString,
                  jar.toURI.toString,
                  meta.supportsHttp,
                  classPath.split(" ").toList.sorted) -> (context.actorSelection(serviceActor.path), loaderOpt)

              }.recover {
                case e: Throwable =>
                  log.error(e, "Error loading the service {}", name)
                  // Remove the actor so we can avoid a badly loaded actor
                  context.child(name) match {
                    case Some(actor) => context.unwatch(actor); context.stop(actor)
                    case None => // Do nothing
                  }
                  None
              }

              serv match {
                case Failure(t) => None
                case Success(None) => None
                case Success(meta) =>
                  log.debug(s"Loaded service $name")
                  Some(meta.asInstanceOf[(ServiceMetaData, (ActorSelection, Option[ServiceClassLoader]))])
              }
            }
            else {
              None
            }
          }
          else {
            None
          }
        } catch {
          case e: Throwable =>
            log.error(e, "Error loading the service: {}", entry.getName)
            None
        }).toMap

      services ++= localServices
    }
    finally {
      jarFile.close()
    }
  }

  def loadService[T](name: String, clazz: Class[T], classLoader: Option[ClassLoader] = None): Option[ActorRef] = {
    if (!classOf[Service].isAssignableFrom(clazz)) {
      log.error(s"Could not load service $name, not assignable from ${clazz.getName}")
      return None
    }

    val classLoaderFinal = classLoader match {
      case None => Thread.currentThread.getContextClassLoader.asInstanceOf[HarnessClassLoader]
      case Some(t) => t
    }
    val loaderOpt = classLoaderFinal.isInstanceOf[ServiceClassLoader] match {
      case true => Some(classLoaderFinal.asInstanceOf[ServiceClassLoader])
      case false => None
    }
    var serviceActor: Option[ActorRef] = None
    val serv = Try {
      serviceActor = Some(context.actorOf(Props(clazz.asInstanceOf[Class[_ <: Actor]]), name))

      // Watch this actor
      context watch serviceActor.get
      // Get the meta data
      val meta = getServiceMetaDetails(context, serviceActor.get)
      ServiceMetaData(name,
        "1.0",
        DateTime.now(),
        "",
        serviceActor.get.path.toString,
        "",
        meta.supportsHttp,
        null) -> (context.actorSelection(serviceActor.get.path), loaderOpt)

    }.recover {
      case f: InvalidActorNameException =>
        log.info(s"Service $name already started")
        serviceActor = getServiceByName(name)
        None
      case e: Throwable =>
        log.error(e, "Error loading the service {}", name)
        // Remove the actor so we can avoid a badly loaded actor
        context.child(name) match {
          case Some(actor) => context.unwatch(actor); context.stop(actor)
          case None => // Do nothing
        }
        None
    }

    serv match {
      case Failure(t) =>
      case Success(None) =>
      case Success(meta) =>
          services ++= Some(meta.asInstanceOf[(ServiceMetaData, (ActorSelection, Option[ServiceClassLoader]))])
    }
    serviceActor
  }

  def getServiceByName(name: String): Option[ActorRef] = {
    var ref: Option[ActorRef] = None
    services foreach { s =>
      if (s._1.name.equals(name)) {
        s._2._1.resolveOne(4 seconds) onComplete {
          case Success(some) => ref = Some(some.asInstanceOf[ActorRef])
          case Failure(f) => log.warn(s"Failed to get service $name")
        }
      }
    }
    if (!ref.isDefined) {
      log.error(s"Was not able to find service $name")
    }
    ref
  }

  /**
   * Send a message to the given service in order to receive it's meta data
   * @param serviceActor The service to send the message to
   * @return An instance of ServiceMetaData
   */
  private def getServiceMetaDetails(context: ActorContext, serviceActor: ActorRef): ServiceMetaDetails = {
    implicit val timeout = Timeout(5 seconds)

    val future = (serviceActor ? GetMetaDetails).mapTo[ServiceMetaDetails]
    // Since we are starting up the service and loading services, we shall block to make
    // sure that everything has run its course
    Await.result(future, timeout.duration)
  }
}
