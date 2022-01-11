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

package com.oracle.infy.wookiee.config

import java.io.{File, IOException}
import java.nio.file.StandardWatchEventKinds._
import java.nio.file._
import akka.actor.Props
import com.oracle.infy.wookiee.app.HActor
import com.sun.nio.file.SensitivityWatchEventModifier
import com.oracle.infy.wookiee.app.HarnessActor.ConfigChange
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent}
import com.oracle.infy.wookiee.service.ServiceManager

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class ConfigWatcherActor extends HActor {
  val configWatcher: WatchService = FileSystems.getDefault.newWatchService()
  var configDir: Path = Paths.get(".")
  val watchThread = new Thread(new DirectoryWatcher)
  var brokenKeys = List()
  var configExists = false

  override def preStart(): Unit = {
    super.preStart()
    ServiceManager.serviceDir(config) match {
      case Some(s) =>
        configDir = s.toPath
        val dirs = s.listFiles.filter(_.isDirectory)

        dirs foreach { dir =>
          val path = Paths.get(dir.getPath.concat("/conf"))
          if (Files.exists(path)) {
            log.info("Adding watcher to existing service directory {} for any *.conf file changes", path)
            path.register(
              configWatcher,
              Array[WatchEvent.Kind[_]](ENTRY_CREATE, ENTRY_MODIFY),
              SensitivityWatchEventModifier.HIGH
            )
          }
        }
        if (dirs.length > 0) {
          configExists = true
          watchThread.start()
        }
      case None => log.debug("Service dir does not exist, not starting watchers")
    }
    System.getProperty("config.file") match {
      case s: String =>
        val cPath = new File(s)
        if (cPath.exists()) {
          Try(cPath.getParentFile.toPath) foreach { path =>
            log.info("Adding watcher to existing config directory {} for any *.conf file changes", path)
            path.register(
              configWatcher,
              Array[WatchEvent.Kind[_]](ENTRY_CREATE, ENTRY_MODIFY),
              SensitivityWatchEventModifier.HIGH
            )

            if (!configExists) {
              configExists = true
              watchThread.start()
            }
          }
        }
      case null =>
        log.info("Prop config.file not set, not watching for config changes")
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    if (watchThread.isAlive) {
      log.info("Stopping config watcher")
      watchThread.interrupt()
    }
  }

  override def checkHealth: Future[HealthComponent] = {
    Future {
      if (!configExists || (watchThread.isAlive && !watchThread.isInterrupted)) {
        HealthComponent("Config Watcher Health", ComponentState.NORMAL, "Config being watched as expected")
      } else
        HealthComponent("Config Watcher Health", ComponentState.CRITICAL, "Config changes are no longer being watched")
    }
  }

  private class DirectoryWatcher extends Runnable {

    def run(): Unit = {
      while (true) {

        // wait for key to be signaled
        var key: Option[WatchKey] = None
        try {
          key = Some(configWatcher.take())
        } catch {
          case _: InterruptedException =>
            return
        }

        key.get.pollEvents().asScala.takeWhile(_.kind() != OVERFLOW) foreach { event =>
          log.debug("Detected alteration on file {}", event.context().toString)
          // The filename is the context of the event.
          val ev = event.asInstanceOf[WatchEvent[Path]]
          val filename = ev.context()

          try {
            // Resolve the filename against the directory.
            val child = configDir.resolve(filename)
            if (filename.toString.endsWith(".conf")) {
              log.debug(
                "Config file change detected, {}, sending message to services/components to reload if applicable.",
                filename.toString
              )
              context.parent ! ConfigChange()
            } else {
              log.debug("Ignoring change to {} as it is not a .conf file", child.toString)
            }
          } catch {
            case x: IOException =>
              log.error("Issue reading file changed in config dir.", x)
          }
        }

        // Reset the key -- this step is critical if you want to receive further watch events.
        val valid = key.get.reset()
        if (!valid) {
          throw new IllegalStateException("Key is no longer in a valid state, can't watch configuration anymore")
        }
      }
    }
  }
}

object ConfigWatcherActor {
  def props: Props = Props[ConfigWatcherActor]()
}
