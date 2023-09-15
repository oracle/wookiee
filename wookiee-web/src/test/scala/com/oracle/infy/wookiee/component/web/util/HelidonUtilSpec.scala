package com.oracle.infy.wookiee.component.web.util

import io.helidon.common.reactive.CompletionSingle
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.Flow
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class HelidonUtilSpec extends AnyWordSpec with Matchers {
  private var throwError: Boolean = false

  "Helidon Util" should {
    "convert a Helidon CompletionAwaitable to a Scala Future" in {
      val completionAwaitable = new Completion()
      val future = HelidonUtil.completionToFuture(completionAwaitable)
      completionAwaitable.await()
      future.isCompleted mustEqual true
    }

    "throw a future error gracefully" in {
      throwError = true
      val completionAwaitable = new Completion()
      val future = HelidonUtil.completionToFuture(completionAwaitable)
      intercept[Exception] {
        Await.result(future, 5.seconds)
      }
    }
  }

  class Completion extends CompletionSingle[String] {

    override def subscribe(subscriber: Flow.Subscriber[_ >: String]): Unit = {
      val stringSubscriber = subscriber.asInstanceOf[Flow.Subscriber[String]]
      stringSubscriber.onSubscribe(new Flow.Subscription {
        override def request(n: Long): Unit = {
          if (throwError) stringSubscriber.onError(new Exception("test"))
          else stringSubscriber.onNext("test")
        }

        override def cancel(): Unit = {}
      })
      if (throwError) stringSubscriber.onError(new Exception("test"))
      else stringSubscriber.onComplete()
    }
  }
}
