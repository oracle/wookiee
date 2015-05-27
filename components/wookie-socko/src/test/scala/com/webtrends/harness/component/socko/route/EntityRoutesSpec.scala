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

package com.webtrends.harness.component.socko.route

import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import com.webtrends.harness.command.{CommandResponse, CommandBean, Command}

import net.liftweb.json.Extraction._
import net.liftweb.json._
import org.junit.runner.RunWith
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.runner.JUnitRunner
import org.specs2.time.NoTimeConversions

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global


class EmptyCommand extends Command with EntityRoutes {

  override def commandName: String = "SockoRoutesSpecTestCommand"

  override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    // just return the bean
    Future {
      bean match {
        case Some(b) => new CommandResponse(Some(compactRender(decompose(bean.get)).asInstanceOf[T]))
        case None => new CommandResponse(Some("No Bean".asInstanceOf[T]))
      }
    }
  }
}

@RunWith(classOf[JUnitRunner])
class EntityRoutesSpec extends SpecificationWithJUnit with NoTimeConversions {

  implicit val system = ActorSystem("sockoRouteSpecSystem")

  val testEntityRoute = TestActorRef[EmptyCommand].underlyingActor

  sequential

  "EntityRoutesRoutes " should {

    "unmarshall application/x-www-form-urlencoded with default charset of utf-8 " in {

      val rawBytes = "cat=meow&dog=bark".getBytes("utf-8")

      val unmarshalled = testEntityRoute.unmarshall[Map[String, String]](rawBytes, "application/x-www-form-urlencoded")

      unmarshalled.isDefined mustEqual true
      unmarshalled.get("cat") mustEqual "meow"
      unmarshalled.get("dog") mustEqual "bark"
    }

    "unmarshall application/x-www-form-urlencoded with non default charset " in {

      val rawBytes = "cat=meow&dog=bark".getBytes("utf-16LE")

      val unmarshalled = testEntityRoute.unmarshall[Map[String, String]](rawBytes,
        "application/x-www-form-urlencoded; charset=utf-16LE")

      unmarshalled.isDefined mustEqual true
      unmarshalled.get("cat") mustEqual "meow"
      unmarshalled.get("dog") mustEqual "bark"
    }

    "throw exception for invalid character set " in {

      val rawBytes = "cat=meow&dog=bark".getBytes("utf-8")

      testEntityRoute.unmarshall[Map[String, String]](rawBytes, "application/x-www-form-urlencoded; charset=UNSUPPORTED") must throwA[Exception]
    }

    "support application/json " in {

      val rawJson = """
          {
           "pets": {
             "cat": "meow",
             "dog": ["bark","woof"]
           },
           "livestock": {
             "cow":"moo"
           }
          }
        """

      val unmarshalled = testEntityRoute.unmarshall[JObject](rawJson.getBytes("utf-8"), "application/json").get

      val json = parse(rawJson).asInstanceOf[JObject]

      json mustEqual unmarshalled

    }

    "ignore charset after application/json " in {

      val rawJson = """
          {
           "pets": {
             "cat": "meow",
             "dog": ["bark","woof"]
           },
           "livestock": {
             "cow":"moo"
           }
          }
                    """

      val unmarshalled = testEntityRoute.unmarshall[JObject](rawJson.getBytes("utf-8"), "application/json; charset=utf-16BE").get

      val json = parse(rawJson).asInstanceOf[JObject]

      json mustEqual unmarshalled

    }

    "support plain/text without a charset " in {

      val rawBytes = "!@#$%^&*()_+".getBytes("utf-8")

      val unmarshalled = testEntityRoute.unmarshall[Map[String, String]](rawBytes, "plain/text").get

      unmarshalled.size mustEqual 1
      unmarshalled("content") mustEqual "!@#$%^&*()_+"

    }

    "support plain/text with a charset " in {

      val rawBytes = "!@#$%^&*()_+".getBytes("utf-16LE")

      val unmarshalled = testEntityRoute.unmarshall[Map[String, String]](rawBytes, "plain/text; charset=UTF-16LE").get

      unmarshalled.size mustEqual 1
      unmarshalled("content") mustEqual "!@#$%^&*()_+"

    }

    "not decode application/json content " in {

      val rawBytes = """{"name": "ho%7B%22me%22%3A%22random%22%7D"}""".getBytes("utf-8")

      val unmarshalled = testEntityRoute.unmarshall[Map[String, String]](rawBytes, "application/json").get

      unmarshalled.size mustEqual 1
      unmarshalled("name") mustEqual "ho%7B%22me%22%3A%22random%22%7D"

    }
  }
}
