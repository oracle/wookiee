/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.infy.wookiee

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.actor.WookieeActor.Receive
import com.oracle.infy.wookiee.app.HarnessActor.ConfigChange
import com.oracle.infy.wookiee.config.ConfigWatcher
import com.oracle.infy.wookiee.health.WookieeMonitor
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.{BufferedWriter, File, FileWriter}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Await
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.reflect.io.{Directory, Path}

class ConfigSpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {
  implicit val dur: FiniteDuration = FiniteDuration(2, TimeUnit.SECONDS)
  new File("services/test/conf").mkdirs()
  writeFile("original = \"value\"")

  implicit val config: Config =
    ConfigFactory.parseString("""
    instance-id = "config-spec"
    services {
     path = "services"
     config-file = "services/test/conf/test.conf"
    }
    """).withFallback(ConfigFactory.load())

  val gotConfigChange: AtomicBoolean = new AtomicBoolean(false)

  val parent: WookieeActor = WookieeActor.actorOf(new WookieeActor {

    val child: ConfigWatcher = WookieeActor.actorOf(new ConfigWatcher(config, {
      self ! ConfigChange()
    }))

    override def getDependents: Iterable[WookieeMonitor] = List(child)

    override def receive: Receive = health orElse {
      case _: ConfigChange =>
        gotConfigChange.set(true)
      case x =>
        child ! x
    }
  })

  "Config " should {
    "be in good health" in {
      val health = Await.result(parent.checkHealth, 5.seconds)
      health.components.head.details mustEqual "Config being watched as expected"
    }

    "detect changes in config" in {
      writeFile("test = \"value\"")
      ThreadUtil.awaitEvent({
        gotConfigChange.get()
      })
    }
  }

  override protected def afterAll(): Unit = {
    Directory(Path(new File("services"))).deleteRecursively()
    ()
  }

  def writeFile(toWrite: String): Unit = {
    val file = new File("services/test/conf/test.conf")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(toWrite)
    bw.close()
  }
}
