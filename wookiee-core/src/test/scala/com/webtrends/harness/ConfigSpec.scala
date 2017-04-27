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
package com.webtrends.harness

import java.io.{BufferedWriter, File, FileWriter}
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.app.HarnessActor.ConfigChange
import com.webtrends.harness.config.ConfigWatcherActor
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import org.specs2.mutable.SpecificationWithJUnit

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.reflect.io.{Directory, Path}

class ConfigSpec extends SpecificationWithJUnit {
  implicit val dur = FiniteDuration(2, TimeUnit.SECONDS)
  new File("services/test/conf").mkdirs()
  implicit val sys = ActorSystem("system", ConfigFactory.parseString( """
    akka.actor.provider = "akka.actor.LocalActorRefProvider"
    services { path = "services" }
    """).withFallback(ConfigFactory.load))

  implicit val ec: ExecutionContextExecutor =  sys.dispatcher

  val probe = TestProbe()
  val parent = sys.actorOf(Props(new Actor {
    val child = context.actorOf(ConfigWatcherActor.props, "child")
    def receive = {
      case x if sender == child => probe.ref forward x
      case x => child forward x
    }
  }))

  sequential

  "config " should {
    "be in good health" in {
      probe.send(parent, CheckHealth)
      val msg = probe.expectMsgClass(classOf[HealthComponent])
      msg.state equals ComponentState.NORMAL
    }

    "detect changes in config" in {
      val file = new File("services/test/conf/test.conf")
      val bw = new BufferedWriter(new FileWriter(file))
      bw.write("test = \"value\"")
      bw.close()
      val msg = probe.expectMsgClass(classOf[ConfigChange])
      msg.isInstanceOf[ConfigChange]
    }
  }

  step {
    sys.terminate().onComplete { _ =>
        Directory(Path(new File("services"))).deleteRecursively()
    }
  }
}

