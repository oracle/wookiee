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

package com.webtrends.harness.component.netty

import akka.actor._
import akka.pattern.ask
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.{ComponentHelper, ComponentRequest}
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.health.HealthResponseType.HealthResponseType
import com.webtrends.harness.health._
import com.webtrends.harness.service.ServiceManager.GetMetaDataByName
import com.webtrends.harness.service.messages.GetMetaData

import scala.util.{Failure, Success}

@SerialVersionUID(1L) case class GetSystemInfo[T](name: String, msg:ComponentRequest[T])
@SerialVersionUID(1L) case class GetHealth(msg: HealthResponseType)
@SerialVersionUID(1L) case class GetServiceInfo(name:Option[String]=None)

/**
 * Created by wallinm on 12/16/14.
 */
protected class CoreNettyWorker extends HActor with ComponentHelper {

  import context._

  val healthActor = actorSelection(HarnessConstants.HealthFullName)
  val serviceActor = actorSelection(HarnessConstants.ServicesFullName)

  /**
   * Establish our routes and other receive handlers
   */
  override def receive = super.receive orElse {
    case GetSystemInfo(name, msg) => respondToComponentRequest(name, msg)
    case GetHealth(msg) => respondToHealthRequest(msg)
    case GetServiceInfo(name) => respondToServiceRequest(name)
  }

  def respondToServiceRequest(name:Option[String]) = {
    val caller = sender()
    val msg = name match {
      case Some(n) => GetMetaDataByName(n)
      case None => GetMetaData(None)
    }
    (serviceActor ? msg) onComplete {
      case Success(resp) => caller ! resp
      case Failure(f) => caller ! Status.Failure(f)
    }
  }

  def respondToComponentRequest[T, M](name:String, msg:ComponentRequest[T]): Unit = {
    val caller = sender()
    componentRequest[T, M](name, msg) onComplete {
      case Success(resp) => caller ! resp
      case Failure(f) => caller ! Status.Failure(f)
    }
  }

  def respondToHealthRequest(msg:HealthResponseType): Unit = {
    val caller = sender()
    (healthActor ? HealthRequest(msg)) onComplete {
      case Success(resp) => caller ! resp
      case Failure(f) => caller ! Status.Failure(f)
    }
  }
}
