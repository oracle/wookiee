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
package com.webtrends.harness.component.zookeeper

import akka.actor.{ActorSystem, Identify}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import org.apache.curator.test.TestingServer
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.time.NoTimeConversions

import scala.concurrent.Await
import scala.concurrent.duration._

class ZookeeperServiceSpec
  extends SpecificationWithJUnit with NoTimeConversions {

  val zkServer = new TestingServer()
  implicit val system = ActorSystem("test", loadConfig)

  lazy val zkActor = system.actorOf(ZookeeperActor.props(ZookeeperSettings(system.settings.config.getConfig("wookiee-zookeeper"))))
  implicit val to = Timeout(2 seconds)

  Await.result(zkActor ? Identify("xyz123"), 2 seconds)
  lazy val service = ZookeeperService()
  Thread.sleep(5000)
  sequential

  "The zookeeper service" should {

    "allow callers to create a node for a valid path" in {
      val res = Await.result(service.createNode("/test", false, Some("data".getBytes)), 1000 milliseconds)
      res shouldEqual "/test"
    }

    "allow callers to create a node for a valid namespace and path" in {
      val res = Await.result(service.createNode("/namespacetest", false, Some("namespacedata".getBytes), Some("space")), 1000 milliseconds)
      res shouldEqual "/namespacetest"
    }

    "allow callers to delete a node for a valid path" in {
      val res = Await.result(service.createNode("/deleteTest", false, Some("data".getBytes)), 1000 milliseconds)
      res shouldEqual "/deleteTest"
      val res2 = Await.result(service.deleteNode("/deleteTest"), 1000 milliseconds)
      res2 shouldEqual "/deleteTest"
    }

    "allow callers to delete a node for a valid namespace and path " in {
      val res = Await.result(service.createNode("/deleteTest", false, Some("data".getBytes), Some("space")), 1000 milliseconds)
      res shouldEqual "/deleteTest"
      val res2 = Await.result(service.deleteNode("/deleteTest", Some("space")), 1000 milliseconds)
      res2 shouldEqual "/deleteTest"
    }

    "allow callers to get data for a valid path " in {
      val res = Await.result(service.getData("/test"), 1000 milliseconds)
      new String(res) shouldEqual "data"
    }

    "allow callers to get data for a valid namespace and path " in {
      val res = Await.result(service.getData("/namespacetest", Some("space")), 1000 milliseconds)
      new String(res) shouldEqual "namespacedata"
    }

    " allow callers to get data for a valid path with a namespace" in {
      val res = Await.result(service.getData("/namespacetest", Some("space")), 1000 milliseconds)
      new String(res) shouldEqual "namespacedata"
    }

    " return an error when getting data for an invalid path " in {
      Await.result(service.getData("/testbad"), 1000 milliseconds) must throwA[Exception]
    }

    " allow callers to get children with no data for a valid path " in {
      val res = Await.result(service.createNode("/test/child", false, None), 1000 milliseconds)
      val res2 = Await.result(service.getChildren("/test", false), 1000 milliseconds)
      res2(0)._1 shouldEqual "child"
      res2(0)._2 shouldEqual None
    }

    " allow callers to get children with data for a valid path " in {
      val res = Await.result(service.setData("/test/child", "data".getBytes), 1000 milliseconds)
      val res2 = Await.result(service.getChildren("/test", true), 1000 milliseconds)
      res2(0)._1 shouldEqual "child"
      res2(0)._2.get shouldEqual "data".getBytes
    }

    " return an error when getting children for an invalid path " in {
      Await.result(service.getChildren("/testbad"), 1000 milliseconds) must throwA[Exception]
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
    zkServer.close
  }

  def loadConfig: Config = {
    ConfigFactory.parseString("""
      wookiee-zookeeper {
        quorum = "%s"
      }
                              """.format(zkServer.getConnectString)
    ).withFallback(ConfigFactory.load()).resolve
  }
}
