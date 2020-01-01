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
package com.webtrends.harness.component

import akka.actor.{ActorRef, Status}
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.app.HActor
import com.webtrends.harness.app.HarnessActor.{ConfigChange, PrepareForShutdown, SystemReady}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

sealed class ComponentMessages()
case class StartComponent() extends ComponentMessages
case class StopComponent() extends ComponentMessages
case class ComponentRequest[T](msg:T, name:Option[String]=None, timeout:Timeout=5 seconds) extends ComponentMessages
case class ComponentMessage[T](msg:T, name:Option[String]=None) extends ComponentMessages

case class ComponentResponse[T](resp:T)

/**
 * Each system component needs to extend this class so that it is loaded up correctly
 */
abstract class Component(name:String) extends HActor with ComponentHelper {
  import context.dispatcher

  protected def defaultChildName:Option[String] = None

  /**
   * Components will typically receive a couple type of messages
   * 1. Start - Will execute after all components have loaded
   * 2. Stop - Will execute prior to shutdown of the harness
   *
   * @return
   */
  override def receive = health orElse {
    case StartComponent => start
    case StopComponent => stop
    case ConfigChange() => // User can receive to do something
    case ComponentRequest(msg, name, timeout) =>
      val caller = sender
      getChildActor(name) match {
        case Some(a) => (a ? msg)(timeout) onComplete {
          case Success(s) => caller ! ComponentResponse(s)
          case Failure(f) => caller ! Status.Failure(f)
        }
        case None => sender ! Status.Failure(ComponentException(this.getClass.getName, s"$name actor not found"))
      }
    case ComponentMessage(msg, name) =>
      getChildActor(name) match {
        case Some(a) => a ! msg
        case None => log.warn("Failed to send message to child actor [$name] for Component")
      }
    case SystemReady => systemReady
    case PrepareForShutdown => prepareForShutdown
  }

  /**
   * Gets the child actor based on a name, if name is None then checks the default child name,
   * if default child name is none then if will check to see if there is only a single child
   * and return that single child
   */
  protected def getChildActor(name:Option[String]) : Option[ActorRef] = {
    name match {
      case Some(ComponentManager.ComponentRef) => Some(self)
      case Some(n) => context.child(n)
      case None =>
        defaultChildName match {
          case Some(dn) => context.child(dn)
          case None =>
            if (context.children.size == 1) {
              Some(context.children.head)
            } else {
              None
            }
        }
    }
  }

  /**
   * Starts the component
   */
  def start() = {
    // after completion of this, we need to send the started message from the component
    context.parent ! ComponentStarted(self.path.name)
  }

  /**
   * Any logic to stop the component
   */
  def stop() = {}

  /**
    * Any logic to run once all components and services are up
    */
  def systemReady() = {}

  /**
    * Any logic to run once we get the shutdown message but before we begin killing actors
    */
  def prepareForShutdown() = {}
}

object Component {
  def getActorPath() : String = {
    s"${HarnessConstants.ComponentName}/"
  }
}
