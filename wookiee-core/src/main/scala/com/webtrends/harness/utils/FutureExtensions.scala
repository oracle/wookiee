/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
      f.onComplete { r =>
        try {
          m(r).onComplete { z =>
            p complete z
          }(ec)
        }catch {
          case ex: Exception => p failure(ex)
        }
      }(ec)
      p.future
    }
  }

}