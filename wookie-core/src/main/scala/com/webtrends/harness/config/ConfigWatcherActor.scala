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

package com.webtrends.harness.config

import java.io.{File, IOException}
import java.nio.file.StandardWatchEventKinds._
import java.nio.file._

import akka.actor.Props
import com.sun.nio.file.SensitivityWatchEventModifier
import com.webtrends.harness.app.HActor
import com.webtrends.harness.app.HarnessActor.ConfigChange
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.ServiceManager

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.control.Breaks._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author woods
 *         2/9/15
 */
class ConfigWatcherActor extends HActor {
  val configWatcher = FileSystems.getDefault.newWatchService()
  var configDir = Paths.get(".")
  val watchThread = new Thread(new DirectoryWatcher)
  var brokenKeys = List()
  var configExists = false

  override def preStart(): Unit = {
    super.preStart()
    ServiceManager.serviceDir(context.system.settings.config) match {
      case Some(s) =>
        configDir = s.toPath
        val dirs = s.listFiles.filter(_.isDirectory)

        dirs foreach {
          dir =>
            val path = Paths.get(dir.getPath.concat("/conf"))
            if (Files.exists(path)) {
              log.info("Adding watcher to existing directory {} for any *.conf file changes", path)
              path.register(configWatcher, Array[WatchEvent.Kind[_]](ENTRY_CREATE), SensitivityWatchEventModifier.HIGH)
              path.register(configWatcher, Array[WatchEvent.Kind[_]](ENTRY_MODIFY), SensitivityWatchEventModifier.HIGH)
            }
        }
        if (dirs.size > 0) {
          configExists = true
          watchThread.start()
        }
      case None => log.warn("Service dir does not exist, not starting watchers")
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
      !configExists || (watchThread.isAlive && !watchThread.isInterrupted) match {
        case true => HealthComponent("Config Watcher Health", ComponentState.NORMAL, "Config being watched as expected")
        case false => HealthComponent("Config Watcher Health", ComponentState.CRITICAL, "Config changes are no longer being watched")
      }
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
          case x: InterruptedException =>
            return
        }

        key.get.pollEvents().toStream.takeWhile(_.kind() != OVERFLOW) foreach {
          event =>
            log.info("Detected alteration on file {}", event.context().toString)
            // The filename is the context of the event.
            val ev = event.asInstanceOf[WatchEvent[Path]]
            val filename = ev.context()

            try {
              // Resolve the filename against the directory.
              val child = configDir.resolve(filename)
              if (filename.toString.endsWith(".conf")) {
                log.info("Config file change detected, {}, sending message to services/components to reload if applicable.", child.toString)
                context.parent ! ConfigChange()
              } else {
                log.info("Ignoring change to {} as it is not a .conf file", child.toString)
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
  def props: Props = Props[ConfigWatcherActor]
}
