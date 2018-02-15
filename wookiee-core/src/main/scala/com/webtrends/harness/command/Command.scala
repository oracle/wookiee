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

package com.webtrends.harness.command

import akka.pattern.pipe
import com.webtrends.harness.app.HActor
import scala.util.{Try, Success, Failure}

import scala.concurrent.Future

/**
 * A command should be a business logic that handles messages from some source (like http) then executes
 * the logic that is defined for the end point and returns the response. It should not ever deal with the
 * underlying messaging protocol but rather focus exclusively on the business logic. It should also not
 * deal with any storage or caching or anything like that.
 *
 * @author Michael Cuthbert on 12/1/14.
 */
trait Command extends BaseCommand with HActor with CommandHelper {
  import context.dispatcher

  override def receive = health orElse ({
    case ExecuteCommand(_, bean, _) => pipe(execute(bean)) to sender
    case _ => // ignore all other messages to this actor
  } : Receive)

  def path : String = s"_wt_internal/${getClass.getSimpleName}"

  /**
   * This allows for extra functionality in your command path. Basically it will allow
   * for multiple paths to be matched to a single command, and will map each path to a
   * given name so that you can find out which path was mapped in the end. By default it
   * will simply be a map of 1 element being retrieved from the default "path" function.
   * So if you only require a single path you wouldn't worry about this function at all
   */
  def paths : Map[String, String] =  Map("default" -> path)

  /**
   * @deprecated Does nothing, free to stop overriding in your Command
   */
  def commandName : String = getClass.getSimpleName

  /**
   * The primary entry point for the command, the actor for this command
   * will ignore all other messaging and only execute through this
   */
  def execute[T:Manifest](bean:Option[CommandBean]=None) : Future[BaseCommandResponse[T]]
}

object Command {
  /**
   * Checks the match between a test path and url path
   *
   * @param commandPath The test path of the command, like /test/$var1/ping
   * @param requestPath The uri requested, like /test/1/ping
   * @return Will return a command bean if matched, None if not and in the command bean the
   *         key var1 will equal 1 as per the example above
   */
  def matchPath(commandPath:String, requestPath:String) : Option[CommandBean] = {

    import com.webtrends.harness.utils.StringPathUtil._

    val bean = new CommandBean()
    val urlPath = requestPath.splitPath()

    val matched = urlPath.corresponds(commandPath.splitPath()) {
      // Convert the segment into an Integer if possible, otherwise leave it as a String
      case (uri, test) if test.head == '$' =>
        val key = test.substring(1)
        Try(uri.toInt) match {
          case Success(v) => bean.addValue(key, v.asInstanceOf[Integer])
          case Failure(_) => bean.addValue(key, uri)
        }
        true

      // Treat the value as a string
      case (uri, test) if test.head == '%' =>
        bean.addValue(test.drop(1), uri)
        true

      // Only match if the value is an INT
      case (uri, test) if test.head == '#' =>
        Try(uri.toInt) match {
          case Success(v) =>
            bean.addValue(test.drop(1), v.asInstanceOf[Integer])
            true
          case Failure(_) =>
            false
        }

      case (uri, test) =>
        test.toLowerCase.split('|').contains(uri.toLowerCase)
    }

    if (matched) Some(bean)
    else None
  }
}
