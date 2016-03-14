/*
 *  Copyright (c) 2016 Webtrends (http://www.webtrends.com)
 *  See the LICENCE.txt file distributed with this work for additional
 *  information regarding copyright ownership.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.webtrends.harness.libs.iteratee

import scala.concurrent.ExecutionContext

object TestExecutionContext {

  /**
   * Create a `TestExecutionContext` that delegates to the iteratee package's default `ExecutionContext`.
   */
  def apply(): TestExecutionContext = new TestExecutionContext(Execution.trampoline)

}

/**
 * An `ExecutionContext` that counts its executions.
 *
 * @param delegate The underlying `ExecutionContext` to delegate execution to.
 */
class TestExecutionContext(delegate: ExecutionContext) extends ExecutionContext {
  top =>

  val count = new java.util.concurrent.atomic.AtomicInteger()

  val local = new ThreadLocal[java.lang.Boolean]

  def preparable[A](body: => A): A = {
    local.set(true)
    try body finally local.set(null)
  }

  def execute(runnable: Runnable): Unit = {
    throw new RuntimeException("Cannot execute unprepared TestExecutionContext")
  }

  def reportFailure(t: Throwable): Unit = {
    println(t)
  }

  override def prepare(): ExecutionContext = {
    val isLocal = Option(local.get()).getOrElse(false: java.lang.Boolean)
    if (!isLocal) throw new RuntimeException("Can only prepare TestExecutionContext within 'preparable' scope")
    val preparedDelegate = delegate.prepare()
    return new ExecutionContext {

      def execute(runnable: Runnable): Unit = {
        count.getAndIncrement()
        preparedDelegate.execute(runnable)
      }

      def reportFailure(t: Throwable): Unit = {
        println(t)
      }

    }
  }

  def executionCount: Int = count.get()

}

