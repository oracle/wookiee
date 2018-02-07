package com.webtrends.harness.utils

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try


object FutureExtensions {

  implicit class FutureExtensions[T](f: Future[T]) {

    // Allows you to map over futures and handle both Success and Failure scenarios in a partial function.
    //
    // For example:
    //    val f: Future[_] = ....
    //    f.mapAll {
    //      case Success(...) => ...
    //      case Failure(t) => ...
    //    }
    def mapAll[Target](m: Try[T] => Target)(implicit ec: ExecutionContext): Future[Target] = {
      val p = Promise[Target]()
      f.onComplete { r =>
        try {
          p success m(r)
        } catch {
          case ex: Exception => p failure(ex)
        }}(ec)
      p.future
    }

    // Allows you to flatMap over futures and handle both Success and Failure scenarios in a partial function.
    //
    // For example:
    //    val f: Future[_] = ....
    //    f.flatMapAll {
    //      case Success(...) => ...
    //      case Failure(t) => ...
    //    }
    def flatMapAll[Target](m: Try[T] => Future[Target])(implicit ec: ExecutionContext): Future[Target] = {
      val p = Promise[Target]()
      f.onComplete { r => m(r).onComplete { z => p complete z }(ec) }(ec)
      p.future
    }
  }

}