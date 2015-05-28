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

class TestSockoHandler2 extends SockoGet with Command {
  override def paths: Map[String, String] = Map("path1" -> "/foo/$var",
    "path2" -> "/foo2",
    "path3" -> "/foo/$var1/$var2")

  override def execute[T](bean: Option[CommandBean]): Future[CommandResponse[T]] = {
    val beanSocko = bean.get.asInstanceOf[SockoCommandBean]
    Future[CommandResponse[T]] {
      val pathSelected = beanSocko(CommandBean.KeyPath)
      val var0 = beanSocko.getOrElse("var", "").toString
      val var1 = beanSocko.getOrElse("var1", "").toString
      val var2 = beanSocko.getOrElse("var2", "").toString
      CommandResponse[T](Some(s"$pathSelected-${List(var0, var1, var2).filter(_.nonEmpty).mkString(",")}".asInstanceOf[T]), "text")
    }
  }

  def getBeanString(bean:CommandBean) : String = bean.values.mkString(",")

  override def commandName: String = "testHandler2"
}
