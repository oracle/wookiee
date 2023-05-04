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
package com.oracle.infy.wookiee.service.messages

import com.oracle.infy.wookiee.service.Service

sealed trait ServiceMessage

case class CheckHealth() extends ServiceMessage

case class Ping() extends ServiceMessage

case class Pong() extends ServiceMessage

case class Ready() extends ServiceMessage

// @deprecated("Phasing out GetMetaDetails, switch to CheckHealth for service status", "2.4.0")
case class GetMetaDetails() extends ServiceMessage

case class LoadService(name: String, clazz: Class[_ <: Service]) extends ServiceMessage
