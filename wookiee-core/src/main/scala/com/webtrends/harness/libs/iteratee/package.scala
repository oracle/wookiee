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
package com.webtrends.harness.libs {

  /**
   * The Iteratee monad provides strict, safe, and functional I/O.
   */
  package object iteratee {

    type K[E, A] = Input[E] => Iteratee[E, A]

  }

}

package com.webtrends.harness.libs.iteratee {

  private[iteratee] object internal {
    import com.webtrends.harness.libs.iteratee.Iteratee
    import scala.concurrent.{ ExecutionContext, Future }
    import scala.util.control.NonFatal

    /**
     * Executes code immediately on the current thread, returning a successful or failed Future depending on
     * the result.
     *
     * TODO: Rename to `tryFuture`.
     */
    def eagerFuture[A](body: => A): Future[A] = try Future.successful(body) catch { case NonFatal(e) => Future.failed(e) }

    /**
     * Executes code in the given ExecutionContext, flattening the resulting Future.
     */
    def executeFuture[A](body: => Future[A])(implicit ec: ExecutionContext): Future[A] = {
      Future {
        body
      }(ec /* Future.apply will prepare */ ).flatMap(identityFunc.asInstanceOf[Future[A] => Future[A]])(Execution.trampoline)
    }

    /**
     * Executes code in the given ExecutionContext, flattening the resulting Iteratee.
     */
    def executeIteratee[A, E](body: => Iteratee[A, E])(implicit ec: ExecutionContext): Iteratee[A, E] = Iteratee.flatten(Future(body)(ec))

    /**
     * Prepare an ExecutionContext and pass it to the given function, returning the result of
     * the function.
     *
     * Makes it easy to write single line functions with a prepared ExecutionContext, eg:
     * {{{
     * def myFunc(implicit ec: ExecutionContext) = prepared(ec)(pec => ...)
     * }}}
     */
    def prepared[A](ec: ExecutionContext)(f: ExecutionContext => A): A = {
      val pec = ec.prepare()
      f(pec)
    }

    val identityFunc: (Any => Any) = (x: Any) => x
  }
}
