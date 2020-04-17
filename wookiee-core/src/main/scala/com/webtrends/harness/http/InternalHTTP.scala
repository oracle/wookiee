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

package com.webtrends.harness.http

import akka.actor.{Props, ActorRef, Actor}

/**
 * @author Michael Cuthbert on 2/3/15.
 */
trait InternalHTTP {
  this: Actor =>
  var httpRef:Option[ActorRef] = None

  def startInternalHTTP(port:Int) : ActorRef = {
    httpRef = Some(context.actorOf(Props(classOf[SimpleHttpServer], port), InternalHTTP.InternalHttpName))
    httpRef.get
  }
}

object InternalHTTP {
  val InternalHttpName = "Internal-Http"
}
