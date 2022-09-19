/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.oracle.infy.wookiee.service

import java.io.File
import java.nio.file.FileSystems
import java.util.jar.Attributes.Name
import java.util.jar.JarFile
import akka.actor._
import akka.pattern.ask
import com.oracle.infy.wookiee.HarnessConstants
import com.oracle.infy.wookiee.app.{HActor, HarnessClassLoader}
import com.oracle.infy.wookiee.component.{ComponentInfo, ComponentReady}
import com.oracle.infy.wookiee.logging.ActorLoggingAdapter
import com.oracle.infy.wookiee.service.messages.GetMetaDetails
import com.oracle.infy.wookiee.service.meta.{ServiceMetaData, ServiceMetaDetails}
import com.oracle.infy.wookiee.utils.ConfigUtil
import org.joda.time.DateTime

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait ServiceLoader { this: HActor with ActorLoggingAdapter =>

  val services: mutable.Map[ServiceMetaData, (ActorSelection, Option[HawkClassLoader])] =
    collection.mutable.HashMap[ServiceMetaData, (ActorSelection, Option[HawkClassLoader])]()
  private val userDir = System.getProperty("user.dir")

  private val libDir = userDir + (if (!new File(userDir + "/lib").exists) {
                                    if (new File(userDir + "/dist").exists) "/dist" else "/target"
                                  } else "")

  private val harnessLibs = FileSystems.getDefault.getPath(libDir + "/lib").toFile.listFiles match {
    case null  => null
    case files => files.filter(_.getName.endsWith("jar"))
  }

  /**
    * Load the services
    * @param context The service manager's context
    */
  def load(context: ActorContext): Unit = {
    Try({
      val internalService = ConfigUtil.getDefaultValue(HarnessConstants.KeyInternalService, config.getString, "")
      if (internalService.nonEmpty) {
        val loader = Thread.currentThread.getContextClassLoader.asInstanceOf[HarnessClassLoader]
        val serviceName = internalService.split("\\.").reverse(0)
        loadService(serviceName, loader.loadClass(internalService))
      } else {
        ServiceManager.serviceDir(config) match {
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
    ()
  }

  /**
    * This method will load a potential service and all of its dependent jars
    * @param context The service manager's context
    * @param rootPath The services root file path
    */
  private def loadClasses(context: ActorContext, rootPath: File): Unit = {
    val jars = rootPath.listFiles().filter(_.getName.endsWith("jar"))
    if (jars.length > 0) {

      val libPath = rootPath.getPath.concat("/lib")
      log.info("Loading the dependent jars in {}", libPath)

      val allJars = Array.concat[File](
        jars,
        new File(libPath)
          .listFiles()
          .filter(_.getName.endsWith("jar"))
          .filterNot(jar => harnessLibs.map(_.getName).contains(jar.getName))
      )

      val urls = allJars.map(_.toURI.toURL)
      val harnessLoader = Thread.currentThread.getContextClassLoader.asInstanceOf[HarnessClassLoader]

      // Load the urls into the class loader
      val loader =
        if (ConfigUtil.getDefaultValue(HarnessConstants.KeyServiceDistinctClassLoader, config.getBoolean, true)) {
          val pcl = HawkClassLoader(HarnessConstants.KeyServiceClassLoaderName, urls.toList, harnessLoader)
          harnessLoader.addChildLoader(pcl)
          pcl
        } else {
          harnessLoader.addURLs(urls.toList)
          harnessLoader
        }

      jars.foreach(file => processJar(context, rootPath, file, loader))
    } else {
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
  private def processJar(context: ActorContext, rootPath: File, jar: File, loader: ClassLoader): Unit = {

    // Open the jar file
    val jarFile = new JarFile(jar)
    val loaderOpt = loader match {
      case loader1: HawkClassLoader => Some(loader1)
      case _                        => None
    }

    try {
      val sClass = classOf[Service]

      val localServices = jarFile
        .entries()
        .asScala
        .flatMap[(ServiceMetaData, (ActorSelection, Option[HawkClassLoader]))](
          entry =>
            try {
              val entryName = entry.getName
              if (entryName.endsWith(".class")) {
                val clazz = loader.loadClass(entryName.replace(".class", "").replaceAll("/", "."))

                if (sClass.isAssignableFrom(clazz) && !clazz.getName.equals(sClass.getName)) {

                  val name = clazz.getSimpleName.toLowerCase
                  val manifest = jarFile.getManifest
                  val build = manifest.getMainAttributes.getValue("Implementation-Build") match {
                    case null            => "N/A"
                    case s if s.nonEmpty => s
                    case _               => "N/A"
                  }
                  val classPath = manifest.getMainAttributes.getValue("Class-Path") match {
                    case null            => "N/A"
                    case s if s.nonEmpty => s
                    case _               => "N/A"
                  }
                  val version = "%s.%s".format(manifest.getMainAttributes.getValue("Implementation-Version"), build)

                  val serv = Try {
                    val serviceActor = context.actorOf(Props(clazz.asInstanceOf[Class[_ <: Actor]]), name)

                    // Watch this actor
                    context watch serviceActor
                    // Get the meta data
                    val meta = getServiceMetaDetails(serviceActor)
                    log.info("{}: {}", name, loader.toString)

                    (
                      ServiceMetaData(
                        name,
                        version,
                        DateTime.now(),
                        rootPath.getAbsolutePath,
                        serviceActor.path.toString,
                        jar.toURI.toString,
                        meta.supportsHttp,
                        classPath.split(" ").toList.sorted
                      ),
                      (context.actorSelection(serviceActor.path), loaderOpt)
                    )

                  }.recover {
                    case e: Throwable =>
                      log.error(e, "Error processing jar for {}", name)
                      // Remove the actor so we can avoid a badly loaded actor
                      context.child(name).foreach { actor =>
                        context.unwatch(actor)
                        context.stop(actor)
                      }
                      None
                  }

                  serv match {
                    case Failure(_)    => None
                    case Success(None) => None
                    case Success(meta) =>
                      log.debug(s"Loaded service $name")
                      Some(meta.asInstanceOf[(ServiceMetaData, (ActorSelection, Option[HawkClassLoader]))])
                  }
                } else {
                  None
                }
              } else {
                None
              }
            } catch {
              case e: Throwable =>
                log.error(e, "Error loading the service: {}", entry.getName)
                None
            }
        )
        .toMap

      services ++= localServices
    } finally {
      jarFile.close()
    }
  }

  def loadService[T](
      name: String,
      clazz: Class[T],
      classLoader: Option[ClassLoader] = None,
      componentInfos: List[ComponentInfo] = List()
  ): Option[ActorRef] = {
    if (!classOf[Service].isAssignableFrom(clazz)) {
      log.error(s"Could not load service $name, not assignable from ${clazz.getName}")
      return None
    }

    val classLoaderFinal = classLoader match {
      case None    => Thread.currentThread.getContextClassLoader.asInstanceOf[HarnessClassLoader]
      case Some(t) => t
    }
    val loaderOpt = classLoaderFinal match {
      case loader: HawkClassLoader => Some(loader)
      case _                       => None
    }
    var serviceActor: Option[ActorRef] = None
    val serv = Try {
      serviceActor = Some(context.actorOf(Props(clazz.asInstanceOf[Class[_ <: Actor]]), name))

      // Watch this actor
      context watch serviceActor.get
      // Get the meta data
      val meta = getServiceMetaDetails(serviceActor.get)
      val file = getClass.getProtectionDomain.getCodeSource.getLocation.getFile
      val version = if (file.endsWith(".jar")) {
        new JarFile(file).getManifest.getMainAttributes.getValue(Name.IMPLEMENTATION_VERSION)
      } else "1.0"
      Some(
        (
          ServiceMetaData(
            name,
            version,
            DateTime.now(),
            "",
            serviceActor.get.path.toString,
            "",
            meta.supportsHttp,
            null
          ),
          (
            context
              .actorSelection(serviceActor.get.path),
            loaderOpt
          )
        )
      )

    }.recover {
      case _: InvalidActorNameException =>
        log.info(s"Service $name already started")
        serviceActor = getServiceByName(name)
        None
      case e: Throwable =>
        log.error(e, "Error loading the service {}", name)
        // Remove the actor so we can avoid a badly loaded actor
        context.child(name) match {
          case Some(actor) => context.unwatch(actor); context.stop(actor)
          case None        => // Do nothing
        }
        None
    }

    serv match {
      case Failure(_)    =>
      case Success(None) =>
      case Success(Some(meta)) =>
        log.debug(s"Service Loader sending [${componentInfos.size}] Component Ready infos")
        componentInfos.foreach { info =>
          meta._2._1 ! ComponentReady(info) // Send readied Component infos to new Service
        }
        services ++= Some(meta)
    }
    serviceActor
  }

  def getServiceByName(name: String): Option[ActorRef] = {
    var ref: Option[ActorRef] = None
    services foreach { s =>
      if (s._1.name.equals(name)) {
        s._2._1.resolveOne(4.seconds) onComplete {
          case Success(some) => ref = Some(some)
          case Failure(_)    => log.warn(s"Failed to get service $name")
        }
      }
    }
    if (ref.isEmpty) {
      log.error(s"Was not able to find service $name")
    }
    ref
  }

  /**
    * Send a message to the given service in order to receive it's meta data
    * @param serviceActor The service to send the message to
    * @return An instance of ServiceMetaData
    */
  private def getServiceMetaDetails(serviceActor: ActorRef): ServiceMetaDetails = {
    val future = (serviceActor ? GetMetaDetails).mapTo[ServiceMetaDetails]
    // Since we are starting up the service and loading services, we shall block to make
    // sure that everything has run its course
    Await.result(future, checkTimeout.duration)
  }
}
