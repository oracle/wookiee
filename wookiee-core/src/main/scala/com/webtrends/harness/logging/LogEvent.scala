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
package com.webtrends.harness.logging

import org.slf4j.{Marker, Logger => Underlying}

private[harness] sealed trait LogEvent {
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

private[harness] case class Error(val logger: Underlying, val message: String,
                                    val marker: Option[Marker] = None, val altSource: Option[String] = None,
                                    val params: Seq[Any] = Nil, val cause: Option[Throwable] = None) extends LogEvent

private[harness] case class Warn(val logger: Underlying, val message: String,
                                   val marker: Option[Marker] = None, val altSource: Option[String] = None,
                                   val params: Seq[Any] = Nil, val cause: Option[Throwable] = None) extends LogEvent

private[harness] case class Info(val logger: Underlying, val message: String,
                                   val marker: Option[Marker] = None, val altSource: Option[String] = None,
                                   val params: Seq[Any] = Nil, val cause: Option[Throwable] = None) extends LogEvent

private[harness] case class Debug(val logger: Underlying, val message: String,
                                    val marker: Option[Marker] = None, val altSource: Option[String] = None,
                                    val params: Seq[Any] = Nil, val cause: Option[Throwable] = None) extends LogEvent

private[harness] case class Trace(val logger: Underlying, val message: String,
                                    val marker: Option[Marker] = None, val altSource: Option[String] = None,
                                    val params: Seq[Any] = Nil, val cause: Option[Throwable] = None) extends LogEvent

