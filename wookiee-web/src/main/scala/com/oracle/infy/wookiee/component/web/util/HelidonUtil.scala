package com.oracle.infy.wookiee.component.web.util

import io.helidon.common.reactive.CompletionAwaitable

import scala.concurrent.{Future, Promise}

object HelidonUtil {

  // Useful function to convert a Helidon CompletionAwaitable to a Scala Future
  def completionToFuture[T](completionAvailable: CompletionAwaitable[T]): Future[T] = {
    val promise = Promise[T]()
    val javaFuture = completionAvailable.toCompletableFuture

    javaFuture.whenComplete { (result, error) =>
      if (error != null) {
        promise.failure(error)
      } else {
        promise.success(result)
      }
    }

    promise.future
  }
}
