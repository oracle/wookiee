/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.component.akkahttp.util

case class TestEntity(v0: String, v1: Double)

case class ErrorEntity(error: String)

case class RequestInfo(
    path: String,
    verb: String,
    headers: Map[String, String],
    segments: List[String],
    queryParams: Map[String, String],
    body: Option[String]
)

case class NotAuthorized(message: String) extends Exception
case class Forbidden(message: String) extends Exception
