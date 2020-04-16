package com.webtrends.harness.utils

import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration.{Duration, FiniteDuration}

class FutureExtensionsSpec extends WordSpecLike with MustMatchers {
  implicit val ec: ExecutionContext = ExecutionContext.global
  case class FutureException(message: String) extends Exception(message)

  import com.webtrends.harness.utils.FutureExtensions._
  val duration: FiniteDuration = Duration.fromNanos(10000000000L)

  "flatMapAll" should {
    "Successfully flatMap Success case" in {
      val f = Future.successful("").flatMapAll {
        case Success(_) => Future.successful("success")
        case Failure(_) => throw new Exception()
      }
      Await.result(f, duration) mustBe "success"
    }

    "Successfully flatMap Failure case" in {
      val f = Future.failed[String](new Exception()).flatMapAll {
        case Success(_) => Future.successful("")
        case Failure(_) => Future.successful("success")
      }
      Await.result(f, duration) mustBe "success"
    }

    "return failed future if exception is thrown in Success case" in {
      val f = Future.successful("").flatMapAll {
        case Success(_) => throw FutureException("Failed")
        case Failure(_) => Future.successful("success")
      }
      an [FutureException] must be thrownBy Await.result(f, duration)
    }

    "return failed future if exception is thrown in Failure case" in {
      val f = Future.failed[String](new Exception()).flatMapAll {
        case Success(_) => Future.successful("success")
        case Failure(_) => throw FutureException("Failed")
      }
      an [FutureException] must be thrownBy Await.result(f, duration)
    }
  }

  "mapAll" should {
    "Successfully map Success case" in {
      val f = Future.successful("").mapAll {
        case Success(_) => "success"
        case Failure(_) => throw new Exception()
      }
      Await.result(f, duration) mustBe "success"
    }

    "Successfully map Failure case" in {
      val f = Future.failed[String](new Exception()).mapAll {
        case Success(_) => throw new Exception()
        case Failure(_) => "success"
      }
      Await.result(f, duration) mustBe "success"
    }

    "Return failed future if exception is thrown in Success case" in {
      val f = Future.successful("").mapAll {
        case Success(_) => throw FutureException("Failed")
        case Failure(_) => "success"
      }
      an [FutureException] must be thrownBy Await.result(f, duration)
    }

    "Return failed future if exception is thrown in Failure case" in {
      val f = Future.failed[String](new Exception()).mapAll {
        case Success(_) => "success"
        case Failure(_) => throw FutureException("Failed")
      }
      an [FutureException] must be thrownBy Await.result(f, duration)
    }
  }
}
