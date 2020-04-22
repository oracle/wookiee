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

package com.webtrends.harness.component

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.command.CommandHelper

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import scala.language.postfixOps

/**
 * This is a helper class that enables developers who use to trait to interact with the ComponentManager
 * easily on the harness
 */
trait ComponentHelper extends CommandHelper {
  this: Actor =>

  import context.dispatcher

  var componentManagerInitialized = false
  var componentManager:Option[ActorRef] = None

  /**
   * This should only be called once to initialize the component manager actor. Will retry when called
   * as many methods require this to work.
   */
  def initComponentHelper : Future[ActorRef] = {
    initCommandHelper

    val p = Promise[ActorRef]()

    def awaitComponentManager(timeOut: Deadline) {
      if (timeOut.isOverdue() && !componentManagerInitialized) {
        componentManagerInitialized = true
        p failure ComponentException("Component Manager", "Failed to get component manager")
      } else if (context != null) {
        context.actorSelection(HarnessConstants.ComponentFullName).resolveOne()(1 second) onComplete {
          case Success(s) =>
            componentManager = Some(s)
            componentManagerInitialized = true
            p success s
          case Failure(_) => awaitComponentManager(timeOut)
        }
      } else p failure ComponentException("Component Manager", "Context set to null, must have shut down")
    }

    componentManager match {
      case Some(cm) => p success cm
      case None =>
        if (!componentManagerInitialized) {
          val deadline = 5 seconds fromNow
          awaitComponentManager(deadline)
        } else {
          p failure ComponentException("Component Manager", "Component manager did not initialize")
        }
    }
    p.future
  }

  /**
   * Wrapper function around request that allows the developer to not have to deal with the
   * ComponentResponse return object, and just deal with the message that they care about
   *
   * @param name name of the component
   * @param msg msg to send to the component
   * @return
   */
  def unwrapRequest[T, U](name:String, msg:ComponentRequest[T]) : Future[U] = {
    val p = Promise[U]()
    componentRequest(name, msg).mapTo[ComponentResponse[U]] onComplete {
      case Success(s) => p success s.resp
      case Failure(f) => p failure f
    }
    p.future
  }

  def request[T](name:String, msg:Any, childName:Option[String]=None) : Future[ComponentResponse[T]] =
    componentRequest(name, ComponentRequest(msg, childName))

  /**
   * Simplest way to make a request directly to a component, will return a Future holding whatever the component returns
   * @param name Name of the component
   * @param msg Message to send it
   */
  def unwrapSelfRequest[T](name:String, msg:AnyRef) : Future[T] = {
    unwrapRequest[msg.type, T](name, ComponentRequest[msg.type](msg, Some(ComponentManager.ComponentRef)))
  }

  /**
   * Wrapper function that allows developer to make requests to components individually without having to know about the
   * ComponentManager as the parent that routes the messages to the various components
   *
   * @param name name of the component
   * @param msg message you want to send to the component
   * @return
   */
  def componentRequest[T, U](name:String, msg:ComponentRequest[T]) : Future[ComponentResponse[U]] = {
    val p = Promise[ComponentResponse[U]]()
    initComponentHelper onComplete {
      case Success(cm) =>
        (cm ? Request(name, msg))(msg.timeout).mapTo[ComponentResponse[U]] onComplete {
          case Success(s) => p success s
          case Failure(f) => p failure f
        }
      case Failure(f) => p failure f
    }
    p.future
  }

  def selfMessage(name:String, msg:Any) =
    componentMessage(name, ComponentMessage(msg, Some(ComponentManager.ComponentRef)))

  /**
   * Wrapper function that will allow you to send any message in and it will
   * wrap the msg within a ComponentMessage case class
   *
   * @param name       name of component
   * @param msg        message to send
   * @param childName  name of component's child, or 'self' if one wants to hit the component itself
   */
  def message(name:String, msg:Any, childName:Option[String]=None) =
    componentMessage(name, ComponentMessage(msg, childName))

  /**
   * Wrapper function that allows the developer to message components individually without having to know about the
   * ComponentManager as the parent that routes the messages to the various components
   *
   * @param name name of the component
   * @param msg message you want to send to the component
   */
  def componentMessage[T](name:String, msg:ComponentMessage[T]) = {
    initComponentHelper onComplete {
      case Success(cm) =>
        cm ! Message(name, msg)
      case Failure(f) => throw f
    }
  }

  /**
   * Wrapper function that allows developers to get the actor reference for a particular component
   *
   * @param name the name of the component
   * @param timeout implicit timeout value
   * @return
   */
  def getComponent(name:String)(implicit timeout:Timeout) : Future[ActorRef] = {
    val p = Promise[ActorRef]()
    initComponentHelper onComplete {
      case Success(cm) =>
        (cm ? GetComponent(name))(timeout).mapTo[Option[ActorRef]] onComplete {
          case Success(s) =>
            s match {
              case Some(ref) => p success ref
              case None => p failure ComponentNotFoundException("Component Manager", s"component $name not found")
            }
          case Failure(f) => p failure f
        }
      case Failure(f) => p failure f
    }
    p.future
  }
}
