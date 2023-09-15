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
package com.oracle.infy.wookiee.component

import akka.actor.{ActorRef, Status}
import akka.pattern.ask
import com.oracle.infy.wookiee.HarnessConstants
import com.oracle.infy.wookiee.app.HActor
import com.oracle.infy.wookiee.app.HarnessActor.{ConfigChange, PrepareForShutdown, SystemReady}
import com.oracle.infy.wookiee.component.ComponentState.ComponentState

import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class StartComponent() extends ComponentMessages
case class ComponentInfoAkka(name: String, state: ComponentState, actorRef: ActorRef) extends ComponentInfo

/**
  * Each system component needs to extend this class so that it is loaded up correctly
  */
abstract case class Component(override val name: String) extends WookieeComponent with HActor with ComponentHelper {
  import context.dispatcher

  protected def defaultChildName: Option[String] = None

  /**
    * Components will typically receive a couple type of messages
    * 1. Start - Will execute after all components have loaded
    * 2. Stop - Will execute prior to shutdown of the harness
    */
  override def receive: Receive = health orElse {
    case StartComponent =>
      start()
    case ConfigChange() => // User can receive to do something
    case ComponentReady(info: ComponentInfo) =>
      onComponentReady(info)
    case ComponentRequest(msg, name) =>
      val caller = sender()
      getChildActor(name) match {
        case Some(a) =>
          (a ? msg)(15.seconds) onComplete {
            case Success(s) => caller ! ComponentResponse(s)
            case Failure(f) => caller ! Status.Failure(f)
          }
        case None => sender() ! Status.Failure(ComponentException(this.getClass.getName, s"$name actor not found"))
      }
    case ComponentMessage(msg, name) =>
      getChildActor(name) match {
        case Some(a) => a ! msg
        case None    => log.warn(s"Failed to send message to child actor [$name] for Component [${this.name}]")
      }
    case SystemReady =>
      systemReady()
    case PrepareForShutdown =>
      prepareForShutdown()
  }

  /**
    * Gets the child actor based on a name, if name is None then checks the default child name,
    * if default child name is none then if will check to see if there is only a single child
    * and return that single child
    */
  protected def getChildActor(name: Option[String]): Option[ActorRef] = {
    name match {
      case Some(ComponentManager.ComponentRef) => Some(self)
      case Some(n)                             => context.child(n)
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
  override def start(): Unit = {
    // after completion of this, we need to send the started message from the component
    context.parent ! ComponentStarted(self.path.name)
  }
}

object Component {

  def getActorPath(): String =
    s"${HarnessConstants.ComponentName}/"
}
