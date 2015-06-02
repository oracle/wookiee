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

import com.webtrends.harness.command.{Command, CommandBean}
import org.mashupbots.socko.events.SockoEvent

/**
 * @author Michael Cuthbert on 1/27/15.
 */
object RoutePath {
  def apply(p:String) = new RoutePath(Map("default" -> p))
  def apply(paths:Map[String, String]) = new RoutePath(paths)
}

class RoutePath(paths:Map[String, String], bean:CommandBean=new CommandBean()) {
  def unapply(ctx: SockoEvent) = {
    var matchFound = false
    paths.toStream.takeWhile(_ => !matchFound) foreach {
      p =>
        Command.matchPath(p._2, ctx.endPoint.path) match {
          case Some(b) =>
            b.addValue(CommandBean.KeyPath, p._1)
            bean.appendMap(b.toMap)
            matchFound = true
          case None => // ignore and continue in the stream
        }
    }

    if (matchFound) {
      Some(ctx.endPoint.path.split("/") filter { x => x.nonEmpty})
    } else {
      None
    }
  }
}
