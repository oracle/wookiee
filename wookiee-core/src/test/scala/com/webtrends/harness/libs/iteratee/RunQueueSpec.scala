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

import scala.language.reflectiveCalls
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.WordSpecLike

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}

class RunQueueSpec extends WordSpecLike with ExecutionSpecification {

  val waitTime: FiniteDuration = Duration(20, SECONDS)

  trait QueueTester {
    def schedule(body: => Future[Unit])(implicit ec: ExecutionContext): Unit
  }

  class RunQueueTester extends QueueTester {
    val rq = new RunQueue()
    def schedule(body: => Future[Unit])(implicit ec: ExecutionContext): Unit = rq.schedule(body)
  }

  class NaiveQueueTester extends QueueTester {
    def schedule(body: => Future[Unit])(implicit ec: ExecutionContext): Unit = Future(body)
  }

  def countOrderingErrors(runs: Int, queueTester: QueueTester)(implicit ec: ExecutionContext): Future[Int] = {
    val result = Promise[Int]()
    val runCount = new AtomicInteger(0)
    val orderingErrors = new AtomicInteger(0)

    for (i <- 0 until runs) {
      queueTester.schedule {
        val observedRunCount = runCount.getAndIncrement()

        // Introduce another Future just to make things complicated :)
        Future {
          // We see observedRunCount != i then this task was run out of order
          if (observedRunCount != i) {
            orderingErrors.incrementAndGet() // Record the error
          }
          // If this is the last task, complete our result promise
          if ((observedRunCount + 1) >= runs) {
            result.success(orderingErrors.get)
          }
        }
      }
    }
    result.future
  }

  "RunQueue" should {

    "run code in order" in {
      import ExecutionContext.Implicits.global

      def percentageOfRunsWithOrderingErrors(runSize: Int, queueTester: QueueTester): Int = {
        val results: Seq[Future[Int]] = for (i <- 0 until 9) yield {
          countOrderingErrors(runSize, queueTester)
        }
        Await.result(Future.sequence(results), waitTime).count(_ > 0) * 10
      }

      // Iteratively increase the run size until we get observable errors 90% of the time
      // We want a high error rate because we want to then use the RunQueueTester
      // on the same run size and know that it is fixing up some problems. If the run size
      // is too small then the RunQueueTester probably isn't doing anything. We use
      // dynamic run sizing because the actual size that produces errors will vary
      // depending on the environment in which this test is run.
      var runSize = 8 // This usually reaches 8192 on my dev machine with 10 simultaneous queues
      var errorPercentage = 0
      while (errorPercentage < 90 && runSize < 1000000) {
        runSize = runSize << 1
        errorPercentage = percentageOfRunsWithOrderingErrors(runSize, new NaiveQueueTester())
      }
      //println(s"Got $errorPercentage% ordering errors on run size of $runSize")

      // Now show that this run length works fine with the RunQueueTester
      percentageOfRunsWithOrderingErrors(runSize, new RunQueueTester()) mustBe 0
    }

    "use the ExecutionContext exactly once per scheduled item" in {
      val rq = new RunQueue()
      mustExecute(1) { implicit runEC =>
        val runFinished = Promise[Unit]()
        rq.schedule {
          runFinished.success(())
          Future.successful(())
        }
        Await.result(runFinished.future, waitTime) mustBe ()
      }
    }
  }

}
