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

package com.webtrends.harness.component.socko.handlers

import com.webtrends.harness.command.{Command, CommandBean, CommandResponse}
import com.webtrends.harness.component.socko.route.SockoGet
import com.webtrends.harness.component.socko.utils.SockoCommandBean
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class TestSockoHandler extends SockoGet with Command {
  override def commandName: String = "testHandler1"
  override def path: String = "foo"

  override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    Future[CommandResponse[T]] {
      CommandResponse[T](Some("bar".asInstanceOf[T]), "text")
    }
  }
}
