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

package com.webtrends.harness.component.socko.command

import com.webtrends.harness.command.{CommandResponse, CommandBean, Command}
import com.webtrends.harness.component.socko.route.{SockoPut, SockoPost, SockoGet}

import scala.concurrent.Future

case class Foo(strVal:String, intVal:Int, doubleVal:Double, longVal:Long) {
  override def toString = {
    s"$strVal,$intVal,$doubleVal,$longVal"
  }
}
/**
 * @author Michael Cuthbert on 2/2/15.
 */
class MarshallCommand extends Command with SockoGet with SockoPost with SockoPut {
  import context.dispatcher

  override def marshallObject(obj: Any, respType: String = "json"): Array[Byte] = {
    val fooObj = obj.asInstanceOf[Foo]
    fooObj.toString.getBytes
  }

  override def unmarshall[T <: AnyRef : Manifest](obj: Array[Byte], contentType: String = "application/json"): Option[T] = {
    if (!obj.isEmpty) {
      val Array(s, i, d, l) = new String(obj).split(",")
      Some(Foo(s, i.toInt, d.toDouble, l.toLong).asInstanceOf[T])
    } else {
      None
    }
  }

  /**
   * Name of the command that will be used for the actor name
   *
   * @return
   */
  override def commandName: String = "MarshallCommand"

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   *
   * @return
   */
  override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = // return a Foo object
    Future {
      bean match {
        case Some(b) =>
          if (b.contains(CommandBean.KeyEntity)) {
            val fo = b.get(CommandBean.KeyEntity).get.asInstanceOf[T]
            CommandResponse(Some(fo))
          } else {
            CommandResponse(Some(Foo("String", 1, 65D, 78L).asInstanceOf[T]))
          }
        case None => CommandResponse(Some(Foo("String", 1, 65D, 78L).asInstanceOf[T]))
      }
    }
}
