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
import akka.util.Timeout
import com.oracle.infy.wookiee.HarnessConstants
import com.oracle.infy.wookiee.app.HActor
import com.oracle.infy.wookiee.app.HarnessActor.{ConfigChange, PrepareForShutdown, SystemReady}

import scala.annotation.nowarn
import scala.concurrent.duration._
import scala.util.{Failure, Success}

sealed class ComponentMessages()
case class StartComponent() extends ComponentMessages

case class ComponentRequest[T](msg: T, name: Option[String] = None, timeout: Timeout = 5.seconds)
    extends ComponentMessages
case class ComponentMessage[T](msg: T, name: Option[String] = None) extends ComponentMessages

case class ComponentResponse[T](resp: T)

/**
  * Each system component needs to extend this class so that it is loaded up correctly
  */
abstract class Component(name: String) extends HActor with ComponentHelper {
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
    case ComponentRequest(msg, name, timeout) =>
      val caller = sender()
      getChildActor(name) match {
        case Some(a) =>
          (a ? msg)(timeout) onComplete {
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
  def start(): Unit = {
    // after completion of this, we need to send the started message from the component
    context.parent ! ComponentStarted(self.path.name)
  }

  /**
    * Any logic to stop the component
    * @deprecated Use prepareForShutdown() instead, shouldn't require additional changes
    */
  @deprecated("Use prepareForShutdown() instead, shouldn't require additional changes", "2.3.26")
  def stop(): Unit = {}

  /**
    * Any logic to run once all (non-hot deployed) components are up
    */
  def systemReady(): Unit = {}

  /**
    * Can override to act when another Wookiee Component comes up. This method will be hit when each
    * other Component in the Service has Started. It will also be back-filled with any Components that
    * reached the Started state before this Component, so no worries about load order. Great for when
    * one needs to use another Component without the need for custom waiting logic, and convenient
    * since it provides the actorRef of that Started Component.
    * Note: The Component will get a Ready message for itself as well
    * @param info Info about the Component that is ready for interaction, name and actor ref.
    *             Note: The 'state' will always be Started
    */
  def onComponentReady(info: ComponentInfo): Unit = {}

  /**
    * Any logic to run once we get the shutdown message but before we begin killing actors
    * When overriding this method, be sure to call super.prepareForShutdown() at the end
    * of the method
    * Note that this calls the deprecated stop() method, and that will be removed at some point
    */
  @nowarn def prepareForShutdown(): Unit = {
    log.info(s"COMP400: [$name] prepared for shutdown")
    // Will keep this in until everyone has moved to prepareForShutdown() instead of stop()
    stop() // scalafix:ok
  }
}

object Component {

  def getActorPath(): String = {
    s"${HarnessConstants.ComponentName}/"
  }
}
