package com.oracle.infy.wookiee.component

import com.oracle.infy.wookiee.app.HarnessClassLoader
import com.oracle.infy.wookiee.component.ComponentState.ComponentState
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.service.HawkClassLoader
import com.oracle.infy.wookiee.utils.{ClassUtil, ConfigUtil, FileUtil}
import com.typesafe.config.{Config, ConfigFactory}

import java.io.File
import java.nio.file.FileSystems
import scala.util.control.Exception.allCatch

trait ComponentInfo {
  val name: String
  val state: ComponentState
}
case class ComponentReady(info: ComponentInfo)
trait ComponentMessages

// @name is deprecated, only used in legacy actor Components
case class ComponentRequest[T](msg: T, name: Option[String] = None) extends ComponentMessages
// @name is deprecated, only used in legacy actor Components
case class ComponentMessage[T](msg: T, name: Option[String] = None) extends ComponentMessages

case class ComponentResponse[T](resp: T)

object ComponentState extends Enumeration {
  type ComponentState = Value
  val Initializing, Started, Failed = Value
}

case class ComponentInfoV2(name: String, state: ComponentState, component: ComponentV2) extends ComponentInfo {

  override def toString: String =
    s"ComponentInfoV2($name, $state, ${ClassUtil.getSimpleNameSafe(component.getClass)})"
}

trait WookieeComponent extends WookieeMonitor {
  def name: String
}

object WookieeComponent extends LoggingAdapter {
  val KeyPathComponents: String = "components.path"

  /**
    * NOTE:: This loads all JARs into the given class loader, don't use a loader here that you want to keep isolated
    * You can create an empty loader like so: 'HarnessClassLoader(new URLClassLoader(Array.empty[URL]))'
    *
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

  def getComponentPath(config: Config): Option[File] = {
    val compDir = FileSystems
      .getDefault
      .getPath(ConfigUtil.getDefaultValue(KeyPathComponents, config.getString, ""))
      .toFile
    if (compDir.exists()) {
      Some(compDir)
    } else None
  }

  // Makes an isolated class loader for each component
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
          log.warn(e.getMessage)
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
                log.warn(s"Failed to parse config file ${co._1.getAbsoluteFile}", fail); None
              case Right(value) => Some(value)
            }
            conf
          } catch {
            case e: IllegalArgumentException =>
              log.warn(e.getMessage)
              None
          }
        }
        configs.toList
      case None => Seq[Config]()
    }
  }

  /**
    * Will validate a component directory and return the location of the component library dir
    *
    * @param componentName name of component .conf file
    * @param folder        folder in which configs can be found
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

  // Grab the first two segments (split by '-') of the file name
  def jarComponentName(file: File): String = {
    val name = file.getName
    val segments = name.split("-")
    // example wookiee-zookeeper-1.0-SNAPSHOT.jar
    segments(0) + "-" + segments(1).replace(".jar", "")
  }
}
