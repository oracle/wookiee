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
package com.oracle.infy.wookiee.component

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.oracle.infy.wookiee.app.HarnessActor.{
  ComponentInitializationComplete,
  ConfigChange,
  PrepareForShutdown,
  SystemReady
}
import com.oracle.infy.wookiee.app.{Harness, HarnessActorSystem, HarnessClassLoader, PrepareForShutdown}
import com.oracle.infy.wookiee.health.{HealthComponent, WookieeHealth}
import com.oracle.infy.wookiee.service.HawkClassLoader
import com.oracle.infy.wookiee.utils.{AkkaUtil, ClassUtil, ConfigUtil, FileUtil}
import com.oracle.infy.wookiee.{HarnessConstants, Mediator}
import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigValueType}

import java.io.File
import java.nio.file.FileSystems
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.control.Exception._
import scala.util.{Failure, Success, Try}

case class Request[T](name: String, msg: ComponentRequest[T])
case class Message[T](name: String, msg: ComponentMessage[T])
case class InitializeComponents()
case class LoadComponent(name: String, classPath: String, classLoader: Option[HarnessClassLoader] = None)
case class ReloadComponent(file: File, classLoader: Option[HarnessClassLoader] = None)
case class ComponentStarted(name: String)
case class GetComponent(name: String)

object ComponentManager extends Mediator[ConcurrentHashMap[(String, ActorSystem), ComponentInfo]] {
  import ComponentState._
  private val externalLogger = log

  val ComponentRef = "self"

  /**
    * Checks to see if all the components have started up
    */
  def isAllComponentsStarted(implicit actorSystem: ActorSystem): Boolean = {
    val groupedMap = componentsForSystem.groupBy(_.state).toList
    if (groupedMap.size == 1) {
      groupedMap.head._1 == Started
    } else {
      groupedMap.isEmpty
    }
  }

  // Returns information for all Components running on an ActorSystem
  def componentsForSystem(implicit actorSystem: ActorSystem): List[ComponentInfo] =
    getMediator(getInstanceId(actorSystem.settings.config)).asScala.values.toList

  // Returns a single Component (if present) on one ActorSystem
  def getComponent(compName: String)(implicit actorSystem: ActorSystem): Option[ComponentInfo] =
    Option(getMediator(getInstanceId(actorSystem.settings.config)).get((compName, actorSystem)))

  /**
    * Checks to see if there are any components that have failed
    *
    * @return Option, if failed will contain name of first component that failed
    */
  def failedComponents(implicit system: ActorSystem): Option[String] = {
    componentsForSystem foreach {
      case x if x.state == Failed => return Some(x.name)
      case _                      => //ignore the other matches
    }
    None
  }

  def props: Props = Props[ComponentManager]()

  val KeyManagerClass = "manager"
  val KeyEnabled = "enabled"

  /**
    * NOTE:: This loads all JARs into the given class loader, don't use a loader here that you want to keep isolated
    * You can create an empty loader like so: 'HarnessClassLoader(new URLClassLoader(Array.empty[URL]))'
    * @param replace When true we will replace current class loaders with the ones discovered in this method
    */
  def loadComponentJars(sysConfig: Config, loader: HarnessClassLoader, replace: Boolean): Unit = {
    getComponentPath(sysConfig) match {
      case Some(dir) =>
        log.debug(s"Looking for Component JARs at ${dir.getAbsolutePath}")
        val hawks = dir.listFiles.collect(getHawkClassLoader(loader)).flatten

        log.info(s"Created Hawk Class Loaders:\n ${hawks.map(_.entityName).mkString("[", ", ", "]")}")
        hawks.foreach(f => loader.addChildLoader(f, replace = replace))
      case None => // ignore
    }
  }

  protected[oracle] def getHawkClassLoader(loader: HarnessClassLoader): PartialFunction[File, Option[HawkClassLoader]] = {
    case file if file.isDirectory =>
      val componentName = file.getName
      try {
        val co = validateComponentDir(componentName, file)
        // get list of all JARS and load each one
        Some(
          HawkClassLoader(
            componentName,
            co._2
              .listFiles
              .filter(f => FileUtil.getExtension(f).equalsIgnoreCase("jar"))
              .map(_.getCanonicalFile.toURI.toURL)
              .toList,
            loader
          )
        )
      } catch {
        case e: IllegalArgumentException =>
          externalLogger.warn(e.getMessage)
          None
      }

    case file if FileUtil.getExtension(file).equalsIgnoreCase("jar") =>
      Some(HawkClassLoader(jarComponentName(file), Seq(file.getCanonicalFile.toURI.toURL), loader))
  }

  /**
    * This function will load all the component Jars from the component location
    * It will check to make sure the structure of the component is valid and return a list
    * of valid enabled configs
    */
  def loadComponentInfo(sysConfig: Config): Seq[Config] = {
    getComponentPath(sysConfig) match {
      case Some(dir) =>
        val configs = dir.listFiles.filter(_.isDirectory) flatMap { f =>
          val componentName = f.getName
          try {
            val co = validateComponentDir(componentName, f)
            // reload config at this point so that it gets the defaults from the JARs
            val conf = allCatch either ConfigFactory.parseFile(co._1) match {
              case Left(fail) =>
                externalLogger.warn(s"Failed to parse config file ${co._1.getAbsoluteFile}", fail); None
              case Right(value) => Some(value)
            }
            conf
          } catch {
            case e: IllegalArgumentException =>
              externalLogger.warn(e.getMessage)
              None
          }
        }
        configs.toList
      case None => Seq[Config]()
    }
  }

  def getComponentPath(config: Config): Option[File] = {
    val compDir = FileSystems
      .getDefault
      .getPath(ConfigUtil.getDefaultValue(HarnessConstants.KeyPathComponents, config.getString, ""))
      .toFile
    if (compDir.exists()) {
      Some(compDir)
    } else None
  }

  /**
    * Will validate a component directory and return the location of the component library dir
    *
    * @param componentName name of component .conf file
    * @param folder folder in which configs can be found
    * @throws IllegalArgumentException if configuration file is not found, or the lib directory is not there
    */
  def validateComponentDir(componentName: String, folder: File): (File, File) = {
    val confFile = folder.listFiles.filter(_.getName.equalsIgnoreCase("reference.conf"))
    require(confFile.length == 1, "Conf file not found.")
    // check the config file and if disabled then fail
    val config = ConfigFactory.parseFile(confFile(0))
    if (config.hasPath(s"$componentName.enabled")) {
      require(config.getBoolean(s"$componentName.enabled"), s"$componentName not enabled")
    }
    val libDir = folder.listFiles.filter(f => f.isDirectory && f.getName.equalsIgnoreCase("lib"))
    require(libDir.length == 1, "Lib directory not found.")
    (confFile(0), libDir(0))
  }

  /**
    * This function will attempt various ways to find the component name of the jar.
    * 1. Will check to see if the jar filename is the component name
    * 2. Will check the mapping list from the config to see if the jar filename maps to a component name
    * 3. Will grab the first two segments of the filename separated by "-" and use that as the component name
    *    eg. "wookiee-socko-1.0-SNAPSHOT.jar" will evaluate the component name to "wookiee-socko"
    * It will perform the checks in the order above
    *
    * @param file The file or directory that is the jar or component dir
    * @return String option if found the name, None otherwise
    */
  def getComponentName(file: File, config: Config): String = {
    val name = file.getName
    if (file.isDirectory) {
      validateComponentDir(name, file)
      name
    } else {
      if (config.hasPath(name)) {
        name
      } else if (config.hasPath(s"${HarnessConstants.KeyComponentMapping}.$name")) {
        config.getString(s"${HarnessConstants.KeyComponentMapping}.$name")
      } else {
        val fn = jarComponentName(file)
        if (config.hasPath(fn)) {
          fn
        } else {
          throw new ComponentNotFoundException("ComponentManager", s"'$fn...' path not found in config")
        }
      }
    }
  }

  def jarComponentName(file: File): String = {
    val name = file.getName
    val segments = name.split("-")
    // example wookiee-zookeeper-1.0-SNAPSHOT.jar
    segments(0) + "-" + segments(1).replace(".jar", "")
  }

  protected[oracle] def setComponentInfo(compInfo: ComponentInfo)(implicit system: ActorSystem): Unit = {
    getMediator(getInstanceId(system.settings.config)).put((compInfo.name, system), compInfo)
    ()
  }
}

class ComponentManager extends PrepareForShutdown {
  import ComponentManager._
  import context.dispatcher

  registerMediator(getInstanceId(config), new ConcurrentHashMap[(String, ActorSystem), ComponentInfo]())

  private val componentTimeout: Timeout = AkkaUtil
    .getDefaultTimeout(config, HarnessConstants.KeyComponentStartTimeout, 20.seconds)

  private var componentsInitialized = false
  implicit val system: ActorSystem = context.system

  override def receive: Receive = initializing

  def initializing: Receive = super.receive orElse {
    case InitializeComponents               => initializeComponents()
    case ComponentStarted(name)             => componentStarted(name, sender())
    case LoadComponent(name, classPath, cl) => sender() ! loadComponentClass(name, classPath, cl)
  }

  def started: Receive = super.receive orElse {
    case Message(name, msg)                 => message(name, msg)
    case Request(name, msg)                 => pipe(request(name, msg)) to sender(); ()
    case GetComponent(name)                 => sender() ! context.child(name)
    case LoadComponent(name, classPath, cl) => sender() ! loadComponentClass(name, classPath, cl)
    case ReloadComponent(file, cl)          => pipe(reloadComponent(file, cl)) to sender(); ()
    case ComponentStarted(name)             => componentStarted(name, sender())
    case SystemReady =>
      context.children.foreach(ref => ref ! SystemReady)
      tryAndLogError(
        componentsForSystem
          .collect({ case ci2: ComponentInfoV2 => ci2 })
          .foreach(ci => ci.component.systemReady()),
        Some("Failed to call systemReady on component")
      )
      ()
    case ConfigChange() =>
      log.debug("Sending config change message to all components...")
      context.children.foreach(ref => ref ! ConfigChange())
    case _ => // Ignore
  }

  override def prepareForShutdown(): Unit = {
    super.prepareForShutdown()
    componentsForSystem
      .collect {
        case ci2: ComponentInfoV2 => ci2
      }
      .foreach(ci => ci.component.prepareForShutdown())
  }

  // TODO: Add in comp v2 support
  protected def reloadComponent(file: File, classLoader: Option[HarnessClassLoader]): Future[Boolean] = {
    try {
      val hClassLoader = getOrDefaultClassLoader(classLoader)
      val updatedConfig = HarnessActorSystem.renewConfigsAndClasses(Some(config), replace = true)
      log.info(s"Updated config: $updatedConfig")
      val compName = getComponentName(file, updatedConfig)

      val stopFuture = (context.child(compName) match {
        case Some(ref) =>
          log.info(s"Component '$compName' already running, stopping current instance")
          Try(ref ! PrepareForShutdown)
          gracefulStop(ref, componentTimeout.duration)
        case None =>
          log.debug(s"Component '$compName' not running, no need to stop")
          Future.successful(true)
      }).recover {
        case ex: Throwable =>
          log.warn(s"Failed to stop Component '$compName', attempting reload anyway", ex)
          false
      }

      stopFuture.map { result =>
        getHawkClassLoader(hClassLoader)(file) match {
          case Some(hcl) =>
            hClassLoader.addChildLoader(hcl)
            if (!result)
              log.warn(s"Note that we didn't stop '$compName' before the timeout")

            findAndLoadComponentManager(compName, updatedConfig)
            true
          case None =>
            log.error(s"Error while getting a hawk class loader for Component [${file.getAbsolutePath}]")
            false
        }
      }
    } catch {
      case ex: Throwable =>
        log.error(s"Could not reload component at [${file.getAbsolutePath}]", ex)
        Future.successful(false)
    }
  }

  private def getOrDefaultClassLoader(classLoader: Option[HarnessClassLoader]) = classLoader match {
    case Some(loader) => loader
    case None         => Thread.currentThread.getContextClassLoader.asInstanceOf[HarnessClassLoader]
  }

  /**
    * Has two phases based on the newly ready Component `compInfo`:
    * 1. Send ComponentReady message to all Started Components (including `compInfo` itself) for this Component
    * 2. Checks for what Components are Started and sends ComponentReady to `compInfo` to catch anything it missed
    */
  private def sendReadinessToAllStarted(compInfo: ComponentInfo): Unit = {
    def sendReady(recipient: ComponentInfo, info: ComponentInfo): Unit = recipient match {
      case recip: ComponentInfoAkka => recip.actorRef ! ComponentReady(info)
      case recip: ComponentInfoV2   => recip.component.onComponentReady(info)
    }

    context.parent ! ComponentReady(compInfo)
    ComponentManager.componentsForSystem.filter(_.state == ComponentState.Started).foreach { info =>
      sendReady(info, compInfo)
      sendReady(compInfo, info)
    }
  }

  private def shutdownOnFailure(): Unit = ComponentManager.failedComponents match {
    case Some(n) =>
      log.error(s"Failed to load component [$n]")
      Harness.shutdown()(context.system)
    case None => //ignore
  }

  private def componentV2Started(compInfo: ComponentInfoV2): Unit = {
    sendReadinessToAllStarted(compInfo)
    if (componentsInitialized) {
      compInfo.component.systemReady()
    } else {
      shutdownOnFailure()
    }
  }

  private def componentStarted(name: String, compRef: ActorRef): Unit = {
    log.debug(s"Received start message from component $name")
    val compInfo = ComponentInfoAkka(name, ComponentState.Started, compRef)
    // Store component info in static map
    ComponentManager.setComponentInfo(compInfo)
    // Send ComponentReady message to all Components/Service and catch this one up on ones it missed
    sendReadinessToAllStarted(compInfo)

    if (componentsInitialized) {
      compRef ! SystemReady
    } else if (ComponentManager.isAllComponentsStarted) {
      validateComponentStartup()
    } else {
      shutdownOnFailure()
    }
  }

  def message[T](name: String, msg: ComponentMessage[T]): Unit = {
    // first check to see if
    context.child(name) match {
      case Some(ref) => ref ! msg
      case None      => log.error(s"Didn't find component $name for messaging")
    }
  }

  /**
    * A message can be sent from any where in the system to any component and get a response
    * @param name name of actor component
    * @param msg message to send to component
    */
  def request[T, U](name: String, msg: ComponentRequest[T]): Future[ComponentResponse[U]] = {
    val p = Promise[ComponentResponse[U]]()
    context.child(name) match {
      case Some(ref) =>
        val actorResp = (ref ? msg)(msg.timeout).mapTo[ComponentResponse[U]]
        actorResp onComplete {
          case Success(resp) => p success resp
          case Failure(f)    => p failure f
        }
      case None => p failure ComponentNotFoundException("ComponentManager", s"Didn't find component $name for requests")
    }
    p.future
  }

  private def validateComponentStartup(): Unit = {
    // Wait for the child actors above to be loaded before calling on the services
    if (context != null && context.children != null) {
      Future.traverse(context.children) { child =>
        (child ? Identify("xyz123"))(componentTimeout)
      } map { _ =>
        sendComponentInitMessage()
      } recover {
        case t: Throwable =>
          log.error("Error loading the component actors", t)
          // if the components failed to load then we will need to shutdown the system
          Harness.shutdown()(context.system)
      }
    }
    ()
  }

  private def sendComponentInitMessage(): Unit = {
    if (!componentsInitialized) {
      // notify the harness actor that we are done
      context.parent ! ComponentInitializationComplete
      componentsInitialized = true
      context.become(started)
      log.info("Component Manager started: {}", context.self.path)
    }
  }

  /**
    * Load up all the system components from the config
    * This function needs to block quite a bit because the system requires to load components
    * prior to anything else happening. This function will only be executed once.
    */
  private def initializeComponents(): Unit = {
    val cList = ComponentManager.getComponentPath(config) match {
      case Some(dir) => dir.listFiles.filter(x => x.isDirectory || FileUtil.getExtension(x).equalsIgnoreCase("jar"))
      case None      => Array[File]()
    }

    val libBuffer = ListBuffer[String]()
    if (config.hasPath(HarnessConstants.KeyComponents)) {
      libBuffer ++= config.getStringList(HarnessConstants.KeyComponents).asScala
    }

    // try find any dynamically, we may get duplicate entries, but that will be handled during the loading process
    libBuffer ++= config
      .root()
      .asScala
      .filter { entry =>
        try {
          val c = config.getConfig(entry._1)
          entry._2.valueType() == ConfigValueType.OBJECT &&
          c.hasPath(HarnessConstants.KeyDynamicComponent) && c.getBoolean(HarnessConstants.KeyDynamicComponent)
        } catch {
          case _: ConfigException =>
            // if this exception occurs we know for sure that it is not a dynamic component
            false
        }
      }
      .keys

    val libList = libBuffer.toList
    val componentsLoaded = mutable.ListBuffer[String]()
    val compList = cList ++ libList
    if (compList.length > 0) {
      // load up configured components
      log.info("Loading components...")
      compList foreach { compFolder =>
        val cfName = compFolder.toString
        try {
          val componentName = compFolder match {
            case f: File   => getComponentName(f, config)
            case s: String => s
            case x         => x.toString
          }
          if (!componentsLoaded.contains(componentName)) {
            findAndLoadComponentManager(componentName, config)
            componentsLoaded += componentName
          }
        } catch {
          case ncd: NoClassDefFoundError =>
            componentLoadFailed(
              cfName,
              s"Could not load component [$compFolder]. No Class Def. This could be because the JAR for the component was not found in the component-path",
              Some(ncd)
            )
          case e: ClassNotFoundException =>
            componentLoadFailed(
              cfName,
              s"Could not load component [$cfName]. Class not found. This could be because the JAR for the component was not found in the component-path",
              Some(e)
            )
          case nf: ComponentNotFoundException =>
            // this is the one case were we don't set the component as failed
            log.warn(s"Could not load component [$cfName]. Component invalid. Error: ${nf.getMessage}", nf)
          case i: IllegalArgumentException =>
            // this is the one case were we don't set the component as failed
            log.warn(s"Could not load component [$cfName]. Component invalid. Error: ${i.getMessage}", i)
          case c: ConfigException =>
            componentLoadFailed(cfName, s"Could not load component [$cfName]. Configuration failure", Some(c))
        }
      }
      // schedule a timeout for all components to send back success start status, and after timeout check the status
      // and if not all started then shutdown the server
      val startTimeout = componentTimeout.duration
      context
        .system
        .scheduler
        .scheduleOnce(startTimeout, new Runnable() {
          def run(): Unit = {
            checkStartupStatus(context.system)
          }
        })
    } else {
      log.info("No components registered.")
      sendComponentInitMessage()
    }
    ()
  }

  /**
    * Will load a component class and initialize it
    * @param componentName Name of component
    * @param className Full class name
    * @param classLoader Class loader for harness
    */
  def loadComponentClass(
      componentName: String,
      className: String,
      classLoader: Option[HarnessClassLoader] = None
  ): Option[ComponentInfo] =
    try {
      val hClassLoader = getOrDefaultClassLoader(classLoader)
      require(className.nonEmpty, "Manager for component not set.")

      val clazz = hClassLoader.loadClass(className)
      clazz match {
        case c if classOf[Component].isAssignableFrom(c) =>
          val info = initComponentActor(componentName, clazz)
          ComponentManager.setComponentInfo(info)
          Some(info)
        case c if classOf[ComponentV2].isAssignableFrom(c) =>
          val info = initComponentV2(componentName, clazz.asSubclass(classOf[ComponentV2]))
          ComponentManager.setComponentInfo(info)
          componentV2Started(info)
          Some(info)
        case _ =>
          log.warn(
            s"Could not load manager [${clazz.getName}] with superclass " +
              s"[${Option(clazz.getSuperclass).map(_.getName).getOrElse("none")}] " +
              s"for [$componentName]. Not an instance of Component"
          )
          None
      }
    } catch {
      case ex: Throwable =>
        log.error(s"Failed to load manager class [$className] for component [$componentName]", ex)
        None
    }

  // New initialization method for V2 components
  def initComponentV2(componentName: String, clazz: Class[_ <: ComponentV2]): ComponentInfoV2 =
    try {
      log.info(s"Loading V2 component [$componentName]")
      val componentStart = ClassUtil.instantiateClass(clazz, componentName, config)
      componentStart.start()
      ComponentInfoV2(componentName, ComponentState.Started, componentStart)
    } catch {
      case ex: Throwable =>
        log.error(s"Failed to load manager class [$clazz] for component [$componentName]", ex)
        ComponentInfoV2(componentName, ComponentState.Failed, ComponentV2(componentName, config))
    }

  /**
    * Initializes the component actor
    */
  def initComponentActor[T](componentName: String, clazz: Class[T]): ComponentInfo = {
    // check to see if the actor exists
    // need to block in this instance we don't want the system to start prior to
    // the system components to being fully loaded.
    context.child(componentName) match {
      case Some(ref) =>
        log.info(s"Component [$componentName] already loaded.")
        getComponent(componentName).getOrElse(
          ComponentInfoAkka(componentName, ComponentState.Initializing, ref)
        )
      case None =>
        val ref = context.actorOf(Props(clazz, componentName), componentName)
        log.info(s"Loading component [$componentName] at path [${ref.path.toString}}]")
        // get the start timeout but default to the component timeout

        ref ! StartComponent
        ComponentInfoAkka(componentName, ComponentState.Initializing, ref)
    }
  }

  /**
    * Will be executed after configured timeout period to make sure that our component manager doesn't just sit around
    * waiting for nothing. If not all components have started then we shut down the server, if it has then we send the
    * all clear message
    */
  private def checkStartupStatus(implicit system: ActorSystem): Unit = {
    if (ComponentManager.isAllComponentsStarted) {
      validateComponentStartup()
    } else {
      log.info("Failed to startup components: " + ComponentManager.componentsForSystem.toString())
      Harness.shutdown()(system)
    }
  }

  private def componentLoadFailed(componentName: String, msg: String, ex: Option[Throwable] = None): Unit = {
    ex match {
      case Some(e) => log.error(msg, e)
      case None    => log.error(msg)
    }
    // Note that since we don't have an ActorRef for the failed Component, we're using ComponentManager's ref instead
    ComponentManager.setComponentInfo(ComponentInfoAkka(componentName, ComponentState.Failed, self))
  }

  private def findAndLoadComponentManager(componentName: String, config: Config): Unit = {
    val compConfig = ConfigUtil.prepareSubConfig(config, componentName)
    if (compConfig.hasPath(ComponentManager.KeyEnabled) && !compConfig.getBoolean(ComponentManager.KeyEnabled)) {
      log.info(s"Component '$componentName' not enabled, won't be started.")
    } else {
      require(
        compConfig.hasPath(ComponentManager.KeyManagerClass),
        s"Manager for component '$componentName' not found in config."
      )
      val className = compConfig.getString(ComponentManager.KeyManagerClass)
      loadComponentClass(componentName, className, Some(HarnessActorSystem.loader))
      ()
    }
  }

  override val name: String = "component-manager"

  // Override to call both Component V1 and V2 health checks
  override protected def checkHealth: Future[HealthComponent] =
    super.checkHealth.flatMap(checkHealthOfChildren(_, this))

  /**
    * Should return a list of child components that will be checked for health
    * and aggregated along with this class's health
    */
  override def getDependentHealths: Iterable[WookieeHealth] =
    getMediator(getInstanceId(config))
      .values()
      .asScala
      .collect {
        case info2: ComponentInfoV2 => info2.component
      }
}
