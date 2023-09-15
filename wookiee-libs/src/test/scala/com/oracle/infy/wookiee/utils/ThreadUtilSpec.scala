package com.oracle.infy.wookiee.utils

import com.oracle.infy.wookiee.utils.ThreadUtil.awaitFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{ExecutionContext, Future}

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
  }
}
