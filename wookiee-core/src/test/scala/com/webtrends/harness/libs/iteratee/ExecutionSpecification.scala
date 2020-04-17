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

import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.ExecutionContext

/**
 * Common functionality for iteratee tests.
 */
trait ExecutionSpecification extends MustMatchers {
  self: WordSpecLike =>

  def testExecution[A](f: TestExecutionContext => A): A = {
    val ec = TestExecutionContext()
    val result = ec.preparable(f(ec))
    result
  }

  def testExecution[A](f: (TestExecutionContext, TestExecutionContext) => A): A = {
    testExecution(ec1 => testExecution(ec2 => f(ec1, ec2)))
  }

  def testExecution[A](f: (TestExecutionContext, TestExecutionContext, TestExecutionContext) => A): A = {
    testExecution(ec1 => testExecution(ec2 => testExecution(ec3 => f(ec1, ec2, ec3))))
  }

  def mustExecute[A](expectedCount: => Int)(f: ExecutionContext => A): A = {
    testExecution { tec =>
      val result = f(tec)
      tec.executionCount mustBe expectedCount
      result
    }
  }

  def mustExecute[A](expectedCount1: Int, expectedCount2: Int)(f: (ExecutionContext, ExecutionContext) => A): A = {
    mustExecute(expectedCount1)(ec1 => mustExecute(expectedCount2)(ec2 => f(ec1, ec2)))
  }

  def mustExecute[A](expectedCount1: Int, expectedCount2: Int, expectedCount3: Int)(f: (ExecutionContext, ExecutionContext, ExecutionContext) => A): A = {
    mustExecute(expectedCount1)(ec1 => mustExecute(expectedCount2)(ec2 => mustExecute(expectedCount3)(ec3 => f(ec1, ec2, ec3))))
  }
}
