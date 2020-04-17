/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.webtrends.harness.logging

import akka.util.Helpers
import org.slf4j.MDC

private[harness] trait BaseLogProcessor {

  val sourceThread = "sourceThread"
  val sourceTime = "sourceTime"
  val akkaSource = "akkaSource"

  /**
   * Make sure we have Java friendly Object instances
   * @param params
   * @return
   */
  protected def transformParams(params: Seq[Any]) = params.map(_.asInstanceOf[Object])

  /**
   * Format the given string template. this is only used when receiving an event which includes
   * a throwable. this is due to the fact that SLF4J does not allow template arguments when including
   * a throwable in its calls.
   * @param template
   * @param arg
   * @return
   */
  protected def format(template: String, arg: Seq[Any]): String = {
    val sb = new java.lang.StringBuilder(64)
    var p = 0
    var rest = template
    while (p < arg.length) {
      val index = rest.indexOf("{}")
      if (index == -1) {
        sb.append(rest).append(" WARNING arguments left: ").append(arg.length - p)
        rest = ""
        p = arg.length
      } else {
        sb.append(rest.substring(0, index)).append(arg(p))
        rest = rest.substring(index + 2)
        p += 1
      }
    }
    sb.append(rest).toString
  }

  @inline
  protected final def withContext(thread: Thread, timestamp: Long, altSource: Option[String])(logStatement: => Unit) {
    if (!thread.getName.equals(Thread.currentThread.getName)) {
      MDC.put(sourceThread, thread.getName)
    }

    MDC.put(akkaSource, if (altSource.isDefined) altSource.get else "")
    MDC.put(sourceTime, Helpers.currentTimeMillisToUTCString(timestamp))

    try logStatement finally {
      MDC.remove(akkaSource)
      MDC.remove(sourceThread)
      MDC.remove(sourceTime)
    }
  }
}
