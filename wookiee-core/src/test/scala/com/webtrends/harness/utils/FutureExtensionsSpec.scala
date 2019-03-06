package com.webtrends.harness.utils

import org.specs2.mutable.SpecificationWithJUnit

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import org.specs2.concurrent.ExecutionEnv

import scala.concurrent.duration.Duration

class FutureExtensionsSpec(implicit ee: ExecutionEnv) extends SpecificationWithJUnit {

  case class FutureException(message: String) extends Exception(message)

  import com.webtrends.harness.utils.FutureExtensions._
  val duration = Duration.fromNanos(10000000000L)

  "flatMapAll" should {
    "Successfully flatMap Success case" in {
      val f = Future.successful("").flatMapAll {
        case Success(_) => Future.successful("success")
        case Failure(_) => throw new Exception()
      }
      Await.result(f, duration) must be equalTo "success"
    }

    "Successfully flatMap Failure case" in {
      val f = Future.failed[String](new Exception()).flatMapAll {
        case Success(_) => Future.successful("")
        case Failure(_) => Future.successful("success")
      }
      Await.result(f, duration) must be equalTo "success"
    }

    "return failed future if exception is thrown in Success case" in {
      val f = Future.successful("").flatMapAll {
        case Success(_) => throw FutureException("Failed")
        case Failure(_) => Future.successful("success")
      }
      Await.result(f, duration) must throwAn[FutureException]
    }

    "return failed future if exception is thrown in Failure case" in {
      val f = Future.failed[String](new Exception()).flatMapAll {
        case Success(_) => Future.successful("success")
        case Failure(_) => throw FutureException("Failed")
      }
      Await.result(f, duration) must throwAn[FutureException]
    }
  }

  "mapAll" should {
    "Successfully map Success case" in {
      val f = Future.successful("").mapAll {
        case Success(_) => "success"
        case Failure(_) => throw new Exception()
      }
      Await.result(f, duration) must be equalTo "success"
    }

    "Successfully map Failure case" in {
      val f = Future.failed[String](new Exception()).mapAll {
        case Success(_) => throw new Exception()
        case Failure(_) => "success"
      }
      Await.result(f, duration) must be equalTo "success"
    }

    "Return failed future if exception is thrown in Success case" in {
      val f = Future.successful("").mapAll {
        case Success(_) => throw FutureException("Failed")
        case Failure(_) => "success"
      }
      Await.result(f, duration) must throwAn[FutureException]
    }

    "Return failed future if exception is thrown in Failure case" in {
      val f = Future.failed[String](new Exception()).mapAll {
        case Success(_) => "success"
        case Failure(_) => throw FutureException("Failed")
      }
      Await.result(f, duration) must throwAn[FutureException]
    }
  }
}
