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
package com.webtrends.harness.component.spray

import akka.actor.ActorDSL._
import akka.actor.Status.Failure
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import spray.http.HttpCharsets._
import spray.http._
import spray.routing.RequestContext

import scala.concurrent.Future

case class TestClass(title: String, value: Int)


class RequestCompletionSpec extends TestKitSpecificationWithJUnit(ActorSystem("test")) {

  implicit def anyToSuccess[T](a: T): org.specs2.execute.Result = success

  import system.dispatcher

  def createResponder: ActorRef = {
    actor(new Act {
      var probe: ActorRef = _
      become {
        case p: ActorRef => probe = p
        case m => probe ! m
      }
    })
  }

  "Completing a request" should {

    "handle a String value" in {
      val responder = createResponder
      val ctx = RequestContext(HttpRequest(), responder, Uri("/").path)

      val p = TestProbe()
      responder ! p.ref

      ctx.complete("test")
      p.expectMsg(HttpResponse(StatusCodes.OK, HttpEntity(ContentType(MediaTypes.`text/plain`, Some(`UTF-8`)), "test")))

    }

    "handle a future value" in {
      val responder = createResponder
      val ctx = RequestContext(HttpRequest(), responder, Uri("/").path)

      val p = TestProbe()
      responder ! p.ref

      ctx.complete[Future[String]](Future {
        "test"
      })
      p.expectMsg(HttpResponse(StatusCodes.OK, HttpEntity(ContentType(MediaTypes.`text/plain`, Some(`UTF-8`)), "test")))
    }

    "handle a future failure" in {
      val responder = createResponder
      val ctx = RequestContext(HttpRequest(), responder, Uri("/").path)

      val p = TestProbe()
      responder ! p.ref

      ctx.complete(Future[String] {
        throw new Exception("test")
      })
      p.expectMsgClass(classOf[Failure])
    }

  }

  step {
    TestKit.shutdownActorSystem(system)
  }

}
