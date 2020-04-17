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

import scala.language.reflectiveCalls

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}
import scala.util.Try

class ExecutionSpec extends WordSpecLike with MustMatchers {
  import Execution.trampoline

  val waitTime: FiniteDuration = Duration(5, SECONDS)

  "trampoline" should {

    "execute code in the same thread" in {
      val f = Future(Thread.currentThread())(trampoline)
      Await.result(f, waitTime) mustBe Thread.currentThread()
    }

    "not overflow the stack" in {
      def executeRecursively(ec: ExecutionContext, times: Int) {
        if (times > 0) {
          ec.execute(() => executeRecursively(ec, times - 1))
        }
      }

      // Work out how deep to go to cause an overflow
      val overflowingExecutionContext = new ExecutionContext {
        def execute(runnable: Runnable): Unit = {
          runnable.run()
        }
        def reportFailure(t: Throwable): Unit = t.printStackTrace()
      }

      var overflowTimes = 1 << 10
      try {
        while (overflowTimes > 0) {
          executeRecursively(overflowingExecutionContext, overflowTimes)
          overflowTimes = overflowTimes << 1
        }
        sys.error("Can't get the stack to overflow")
      } catch {
        case _: StackOverflowError => ()
      }

      // Now verify that we don't overflow
      Try(executeRecursively(trampoline, overflowTimes)).isSuccess mustBe true
    }

    "execute code in the order it was submitted" in {
      val runRecord = scala.collection.mutable.Buffer.empty[Int]
      case class TestRunnable(id: Int, children: Runnable*) extends Runnable {
        def run(): Unit = {
          runRecord += id
          for (c <- children) trampoline.execute(c)
        }
      }

      trampoline.execute(
        TestRunnable(0,
          TestRunnable(1),
          TestRunnable(2,
            TestRunnable(4,
              TestRunnable(6),
              TestRunnable(7)),
            TestRunnable(5,
              TestRunnable(8))),
          TestRunnable(3))
      )

      runRecord mustBe (0 to 8)
    }

  }

}
