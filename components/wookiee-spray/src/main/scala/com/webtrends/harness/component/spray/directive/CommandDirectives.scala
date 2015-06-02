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

package com.webtrends.harness.component.spray.directive

import com.webtrends.harness.command.{Command, CommandBean}
import spray.http.HttpHeader
import spray.routing.{Directive0, Directive1}

/**
 * @author Michael Cuthbert on 12/12/14.
 */
trait CommandDirectives extends BaseDirectives {

  /**
   * Matches a string path like '/test/ping' to the current uri and returns
   * a command bean if matched. Will also extract segments (designated with the
   * first char value '$' in the test path), and place those values into the
   * command bean with the key's defined in the path. So path variable $var1
   * would be the key "var1"
   *
   * @param path the path that the user is trying to match.
   * @return a commandbean containing any segments extracted from the url
   */
  def commandPath(path:String) : Directive1[CommandBean] = {
    extract(_.request.uri.path.toString()) flatMap {
      pa => Command.matchPath(path, pa) match {
        case Some(s) => provide(s)
        case None => reject
      }
    }
  }

  /**
   * Exactly like commandPath except it will evaluate over a set of paths and return
   * the first matched path. There is no ordering, it is expected that each path in the
   * set are mutually exclusive.
   *
   * @param paths The set of paths that a user wishes to match.
   * @return
   */
  def commandPaths(paths:Map[String, String]) : Directive1[CommandBean] = {
    extract(_.request.uri.path.toString()) flatMap {
      pa =>
        var matchFound = false
        val retBean = new CommandBean()
        paths.toStream.takeWhile(_ => !matchFound) foreach {
          path => Command.matchPath(path._2, pa) match {
            case Some(b) =>
              b.addValue(CommandBean.KeyPath, path._1)
              matchFound = true
              retBean.appendMap(b.toMap)
            case None => //ignore and continue
          }
        }
        if (matchFound) {
          provide(retBean)
        } else {
          reject
        }
    }
  }

  /**
   * Takes a map that adds specific headers to specific requests http method types
   *
   * @param headers map of headers http method -> list of headers
   * @return
   */
  def mapHeaders(headers:Map[String, List[HttpHeader]]) : Directive0 = {
    extract(_.request.method) flatMap {
      method =>
        val retHeaders = (headers filter {
          x => x._1.equalsIgnoreCase(method.name) || x._1 == CommandDirectives.KeyAllHeaders
        } values).flatten.toList
        respondWithHeaders(retHeaders) hflatMap {
          case _ => pass
        }
    }
  }
}

object CommandDirectives {
  val KeyAllHeaders = "all"
}
