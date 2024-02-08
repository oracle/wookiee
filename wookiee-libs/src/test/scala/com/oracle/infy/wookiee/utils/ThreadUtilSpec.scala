package com.oracle.infy.wookiee.utils

import com.oracle.infy.wookiee.utils.ThreadUtil.{awaitFuture, futureWithTimeout}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}

class ThreadUtilSpec extends AnyWordSpec with Matchers {

  implicit val ec: ExecutionContext = ExecutionContext.global

  "The awaitFuture function" should {

    "return successfully when the future returns true immediately" in {
      def futureToTest(): Future[Boolean] = Future {
        true
      }
      awaitFuture(futureToTest, waitMs = 5000) // This should not throw any exceptions
    }

    "throw a RuntimeException when the future returns false and times out" in {
      def futureToTest(): Future[Boolean] = Future {
        false
      }
      intercept[RuntimeException] {
        awaitFuture(futureToTest, waitMs = 1000)
      }
    }

    "throw a RuntimeException when the future fails and ignoreError is false" in {
      def futureToTest(): Future[Boolean] = Future {
        throw new Exception("Failure attempt")
      }

      intercept[Exception] {
        awaitFuture(futureToTest, ignoreError = false)
      }
    }

    "return successfully when the future throws exceptions but eventually returns true" in {
      @volatile
      var attempt = 0
      def futureToTest(): Future[Boolean] = Future {
        attempt += 1
        if (attempt <= 3) {
          throw new Exception("Failure attempt")
        }
        true
      }
      awaitFuture(futureToTest) // This should not throw any exceptions
    }

    "return successfully when the future returns false but eventually returns true" in {
      var attempt = 0
      def futureToTest(): Future[Boolean] = Future {
        attempt += 1
        if (attempt <= 3) {
          false
        } else {
          true
        }
      }
      awaitFuture(futureToTest, waitMs = 5000, retryIntervalMs = 500) // This should not throw any exceptions
    }

    "throw a RuntimeException when the future never returns true and times out" in {
      def futureToTest(): Future[Boolean] = Future {
        false
      }
      intercept[RuntimeException] {
        awaitFuture(futureToTest, waitMs = 1000, retryIntervalMs = 500)
      }
    }

    "wait on a future to complete successfully" in {
      val future = Future {
        true
      }
      Await.result(futureWithTimeout(future, 10.seconds), 15.seconds) mustEqual true
    }

    "return timeoutexception when future takes too long" in {
      val future = Future {
        Thread.sleep(5000L)
      }
      intercept[TimeoutException] {
        Await.result(futureWithTimeout(future, 1.milliseconds), 15.seconds)
      }
    }

    "process futures with timeouts in a performant fashion" in {
      var currentTime = System.currentTimeMillis()
      Await.result(Future.sequence(1.until(500).map(_ => Future { Thread.sleep(10L) }).toList), 15.seconds)
      println(s"Normal future execution time: ${System.currentTimeMillis() - currentTime}")
      currentTime = System.currentTimeMillis()
      Await.result(
        Future.sequence(1.until(500).map(_ => futureWithTimeout(Future { Thread.sleep(10L) }, 15.seconds))),
        15.seconds
      )
      println(s"Timeout'd future execution time: ${System.currentTimeMillis() - currentTime}")
    }
  }
}
