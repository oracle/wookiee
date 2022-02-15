package com.oracle.infy.wookiee.component

import com.oracle.infy.wookiee.HarnessConstants._
import com.oracle.infy.wookiee.app.{HActor, HarnessClassLoader}
import com.oracle.infy.wookiee.component.ComponentReloadActor.CheckForChanges
import com.oracle.infy.wookiee.health.HealthComponent
import com.sun.nio.file.SensitivityWatchEventModifier

import java.io.File
import java.nio.file.StandardWatchEventKinds._
import java.nio.file._
import scala.jdk.CollectionConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

object ComponentReloadActor {
  case class CheckForChanges()
}

/**
  * Enabled by setting 'components.dynamic-loading' to 'true'
  * Used to monitor the directories specified at 'components.path' and 'components.lib-components'
  * When an add/modify is detected in the JARs in those dirs then we will:
  *  1) Stop the current actor associated with that Component if it already exists
  *  2) Discard the HawkClassLoader for that Component if it existed
  *  3) Load up the classes in that JAR via a new HawkClassLoader
  *  4) Start up the main Component actor for that new JAR
  */
class ComponentReloadActor(loader: HarnessClassLoader) extends HActor with ComponentHelper {
  import context.dispatcher
  val componentWatcher: WatchService = FileSystems.getDefault.newWatchService()

  override def preStart(): Unit = {
    try {
      if (!config.hasPath(KeyDynamicLoading) || !config.getBoolean(KeyDynamicLoading)) {
        log.info(s"Setting '$KeyDynamicLoading' was false, not monitoring for dynamic loading of Components")
        context.stop(self)
      } else {
        // Start up monitoring of our directories
        val toWatch = watchDirs()
        log.info(
          "Starting up Component Reload Actor for dynamic loading of " +
            s"Components in the directories: [${toWatch.mkString(",")}]"
        )

        val watched = toWatch.flatMap { dir =>
          val path = Paths.get(new File(dir).getPath)
          if (Files.exists(path)) {
            log.debug("Adding watcher to Component directory [{}]", path)
            Some(
              path.register(
                componentWatcher,
                Array[WatchEvent.Kind[_]](ENTRY_CREATE),
                SensitivityWatchEventModifier.MEDIUM
              )
            )
          } else None
        }

        if (watched.isEmpty) {
          log.warn(
            s"None of the directories configured at '$KeyPathComponents' or '$KeyComponents' were found, not watching"
          )
          context.stop(self)
        } else {
          log.debug("Successfully setup directory watching for Component Reloads")
          self ! CheckForChanges()
        }
      }
    } catch {
      case ex: Throwable =>
        log.error("Unexpected error while setting up Component Reload Actor", ex)
        context.stop(self)
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info("Stopping Component Reload Actor")
  }

  override def receive: Receive = super.receive orElse {
    case _: CheckForChanges =>
      changeChecker()
      ()
    case _ => // Ignore
  }

  // Not blocking so health checks can get through
  def changeChecker(): Future[Unit] = Future {
    try {
      // wait for key to be signaled
      val key = componentWatcher.take()

      val events = key.pollEvents().asScala.takeWhile(_.kind() != OVERFLOW).toList
      events.foreach { event =>
        log.info("Detected new JAR/Directory at {}", event.context().toString)
        val ev = event.asInstanceOf[WatchEvent[Path]]
        val file = truePath(ev.context().toFile)

        reloadComponentFromFile(file, Some(loader)).foreach { result =>
          if (result) log.info(s"Successfully sent Component at [${file.getAbsolutePath}] to reload")
          else log.error(s"Component Manager reported failed reload of Component at [${file.getAbsolutePath}]")
        }
      }

      // Reset the key -- this step is critical if you want to receive further watch events.
      val valid = key.reset()
      if (!valid) {
        log.error("Key is no longer in a valid state, can't watch Components anymore")
        context.stop(self)
      } else {
        self ! CheckForChanges()
      }
    } catch {
      case _: InterruptedException =>
        log.warn("Got interrupt exception while watching for component changes, closing down watcher")
        context.stop(self)
      case ex: Throwable =>
        log.warn("Error in Component Reload Actor, will try again in 15 seconds", ex)
        context.system.scheduler.scheduleOnce(15.seconds, self, CheckForChanges())
        ()
    }
  }

  private def truePath(file: File): File = {
    val toCheck = watchDirs()
    toCheck.map(dir => new File(s"$dir/${file.getName}")).find(_.exists()).getOrElse(file)
  }

  private def watchDirs(): List[String] = {
    val mainDir = config.getString(KeyPathComponents)
    val libDir = if (config.hasPath(KeyComponents)) Some(config.getString(KeyComponents)) else None

    List(Some(mainDir), libDir).flatten
  }

  override def checkHealth: Future[HealthComponent] = {
    Future {
      HealthComponent("Component Reload Actor", details = "Watching for changes in Component directories")
    }
  }
}
