package com.oracle.infy.wookiee.app

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.actor.WookieeScheduler
import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.component.WookieeComponent
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import java.io.{File, FilenameFilter, InputStream}
import java.nio.file.FileSystems
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.Try
import scala.util.control.Exception.allCatch

object WookieeSupervisor extends Mediator[WookieeSupervisor] {
  val KeyServicePath = "services.path"

  lazy val loader: HarnessClassLoader = HarnessClassLoader(Thread.currentThread.getContextClassLoader)

  // JARs are reloaded onto the classpath in this method if replace = true, in ComponentManager.loadComponentJars
  def renewConfigsAndClasses(config: Option[Config], replace: Boolean = false): Config = {
    def printConf(conf: Config): String =
      conf.root().render(ConfigRenderOptions.concise())

    var sysConfig = {
      if (config.isDefined) {
        config.get
      } else {
        val baseConfig = ConfigFactory.load(loader, "conf/application.conf")
        ConfigFactory.load(loader).withFallback(baseConfig).getConfig("wookiee-system")
      }
    }

    WookieeComponent.loadComponentJars(sysConfig, loader, replace = replace)
    for (child <- loader.getChildLoaders) {
      def readRefConf(): Option[Config] = {
        val re = child.getResources("reference.conf")
        while (re.hasMoreElements) {
          val next = re.nextElement()
          if (next.getPath.contains(child.entityName)) {
            val confStr = Source.fromInputStream(next.getContent.asInstanceOf[InputStream]).mkString
            val childConf = ConfigFactory.parseString(confStr)
            log.info(
              s"New config for extension '${child.entityName}', jar: '${child.urls.head.getPath}': " +
                s"\n${printConf(childConf)}"
            )
            return Some(childConf)
          }
        }
        None
      }

      readRefConf() match {
        case Some(conf) =>
          sysConfig = sysConfig.withFallback(conf)
        case None =>
          log.warn(s"Didn't find 'reference.conf' in jar file '${child.urls.head.getPath}'")
      }
    }
    ConfigFactory.load

    log.debug("Loading the service configs")
    val configs = loadConfigs(sysConfig)
    if (configs.nonEmpty)
      log.info(
        s"${configs.size} service config(s) have been loaded: \n${configs.map(printConf).mkString(", ")}"
      )

    log.debug("Loading the component configs")
    val compConfigs = WookieeComponent.loadComponentInfo(sysConfig)
    if (compConfigs.nonEmpty)
      log.info(
        s"${compConfigs.size} component config(s) have been loaded: \n${compConfigs.map(printConf).mkString(", ")}\nIf 0 could be due to config loaded from component JARs."
      )

    val allConfigs = configs ++ compConfigs

    // Build the hierarchy
    val conf =
      if (allConfigs.isEmpty) sysConfig
      else allConfigs.reduce(_.withFallback(_)).withFallback(sysConfig)
    val finalConf = conf.resolve()
    log.debug(s"Used configuration: \n${printConf(finalConf)}")
    finalConf
  }

  /**
    * Load the configuration files for the deployed services
    *
    * @param sysConfig System level config for wookiee
    */
  def loadConfigs(sysConfig: Config): Seq[Config] = {
    serviceDir(sysConfig) match {
      case Some(s) =>
        val dirs = s.listFiles.filter(_.isDirectory)

        val configs = dirs flatMap { dir =>
          val path = dir.getPath.concat("/conf")
          log.info("Checking the directory {} for any *.conf files to load", path)
          for {
            file <- getConfigFiles(path)
            conf = allCatch either ConfigFactory.parseFile(file) match {
              case Left(fail)   => log.error(s"Could not load the config file ${file.getAbsolutePath}", fail); None
              case Right(value) => Some(value)
            }
            if conf.isDefined
          } yield conf.get
        }
        configs.toList
      case None => Seq()
    }
  }

  /**
    * Get the services directory
    *
    * @param config The systems main config
    * @return The service root path, this is option, so if none then not found
    */
  def serviceDir(config: Config): Option[File] = {
    val file = FileSystems.getDefault.getPath(config.getString(KeyServicePath)).toFile
    if (file.exists()) {
      Some(file)
    } else {
      None
    }
  }

  private def getConfigFiles(path: String): Seq[File] = {
    val root = new File(path)
    if (root.exists) {
      root
        .listFiles(new FilenameFilter {
          def accept(dir: File, name: String): Boolean = name.endsWith(".conf")
        })
        .toList
    } else Seq.empty
  }
}

class WookieeSupervisor(config: Config)(implicit ec: ExecutionContext) extends WookieeMonitor {
  import WookieeSupervisor._
  registerMediator(getInstanceId(config), this)
  private val healthComponents = new TrieMap[String, WookieeMonitor]()

  override val name: String = "wookiee-supervisor"

  def startSupervising(): Unit = {
    log.info("Starting to supervise critical constituents of Wookiee..")
    // Set the thread pool size for the scheduler
    WookieeScheduler.setThreads(Try(config.getInt("execution.scheduled-thread-pool-size")).getOrElse(32))
    // Initialize the Command Executive
    val wookieeCommandManager = new WookieeCommandExecutive("wookiee-commands", config)
    healthComponents.put(wookieeCommandManager.name, wookieeCommandManager)
    log.info("Wookiee now under supervision")
  }

  override def getDependents: Iterable[WookieeMonitor] = healthComponents.values
}
