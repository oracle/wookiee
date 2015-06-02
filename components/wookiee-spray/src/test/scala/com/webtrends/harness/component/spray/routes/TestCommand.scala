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

package com.webtrends.harness.component.spray.routes

import com.webtrends.harness.command.{CommandException, CommandResponse, CommandBean, Command}
import com.webtrends.harness.component.spray.route._
import spray.http.ContentTypes
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import spray.routing.Route

import scala.concurrent.Future

/**
 * This entire file consists of Test commands for all the different method types, to make sure all the different
 * scenarios work. Including marshalling and unmarshalling and all the custom functionality behind it.
 *
 * @author Michael Cuthbert on 12/3/14.
 */
sealed abstract class TestCommand extends Command {
  implicit val executionContext = context.dispatcher
  override def path: String = "/foo/$key/bar/$key2"

  override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    Future {
      bean match {
        case Some(b) =>
          CommandResponse(Some(b.asInstanceOf[T]), "json")
        case None => CommandResponse(Some("NONE".asInstanceOf[T]), "txt")
      }
    }
  }
}

class BaseTestCommand extends TestCommand with SprayGet with SprayHead with SprayOptions with SprayPatch {
  override def commandName: String = "BaseTest"
}

case class TestObject(stringKey:String, intKey:Int)

sealed abstract class EntityBase extends TestCommand {
  override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    Future {
      bean match {
        case Some(b) =>
          val testObj = b(CommandBean.KeyEntity).asInstanceOf[TestObject]
          CommandResponse(Some(testObj.asInstanceOf[T]), "json")
        case None => throw CommandException("EntityTest", "No bean supplied")
      }
    }
  }
}

class EntityTestCommand extends EntityBase with SprayPost {
  override def commandName: String = "EntityTest"
  override def setRoute = postRoute[TestObject]
}

class MarshallTestCommand extends EntityBase with SprayPut {
  override def commandName: String = "MarshallTest"
  override def setRoute = putRoute[TestObject]

  // Below is a custom unmarshaller if you want to use it differently
  // The Create command will use a custom unmarshaller and mimetype
  // and the Update command will use the default unmarshaller using JSON
  override implicit def InputUnmarshaller[T:Manifest] =
    Unmarshaller.delegate[String, T]() {
      obj =>
        val Array(sKey, iKey) = obj.split(":")
        TestObject(sKey, Integer.parseInt(iKey)).asInstanceOf[T]
    }
  // Below is a custom marshaller if you want to use it differently
  // The Create command will use a custom marshaller and mimetype
  // and the Update command will use the default marshaller
  override implicit def OutputMarshaller[T<:AnyRef] =
    Marshaller.delegate[T, String](ContentTypes.`text/plain`) {
      obj =>
        val tObj = obj.asInstanceOf[TestObject]
        s"String is ${tObj.stringKey} and Int is ${tObj.intKey}"
    }
}

class CustomTestCommand extends TestCommand with SprayCustom {
  override def customRoute: Route = {
    path("foo" / "bar") {
      get {
        innerExecute()
      }
    }
  }
  override def commandName: String = "CustomTest"
}
