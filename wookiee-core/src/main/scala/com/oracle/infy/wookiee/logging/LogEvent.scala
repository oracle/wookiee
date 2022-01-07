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
package com.oracle.infy.wookiee.logging

import org.slf4j.{Marker, Logger => Underlying}

private[oracle] sealed trait LogEvent {

  @transient
  val thread: Thread = Thread.currentThread
  val timestamp: Long = System.currentTimeMillis

  def logger: Underlying

  def message: String

  def marker: Option[Marker]

  def altSource: Option[String]

  def params: Seq[Any]

  def cause: Option[Throwable]
}

private[oracle] case class Error(
    logger: Underlying,
    message: String,
    marker: Option[Marker] = None,
    altSource: Option[String] = None,
    params: Seq[Any] = Nil,
    cause: Option[Throwable] = None
) extends LogEvent

private[oracle] case class Warn(
    logger: Underlying,
    message: String,
    marker: Option[Marker] = None,
    altSource: Option[String] = None,
    params: Seq[Any] = Nil,
    cause: Option[Throwable] = None
) extends LogEvent

private[oracle] case class Info(
    logger: Underlying,
    message: String,
    marker: Option[Marker] = None,
    altSource: Option[String] = None,
    params: Seq[Any] = Nil,
    cause: Option[Throwable] = None
) extends LogEvent

private[oracle] case class Debug(
    logger: Underlying,
    message: String,
    marker: Option[Marker] = None,
    altSource: Option[String] = None,
    params: Seq[Any] = Nil,
    cause: Option[Throwable] = None
) extends LogEvent

private[oracle] case class Trace(
    logger: Underlying,
    message: String,
    marker: Option[Marker] = None,
    altSource: Option[String] = None,
    params: Seq[Any] = Nil,
    cause: Option[Throwable] = None
) extends LogEvent
