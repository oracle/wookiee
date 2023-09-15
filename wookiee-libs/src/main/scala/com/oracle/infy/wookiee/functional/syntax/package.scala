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
package com.oracle.infy.wookiee.functional.syntax

import com.oracle.infy.wookiee.functional._

import scala.language.implicitConversions

/**
  * Don't forget to {{{import com.oracle.infy.wookiee.functional.syntax._}}} to enable functional combinators
  * when using Json API.
  */
object `package` {

  implicit def toAlternativeOps[M[_], A](a: M[A])(implicit app: Alternative[M]): AlternativeOps[M, A] =
    new AlternativeOps(a)

  implicit def toApplicativeOps[M[_], A](a: M[A])(implicit app: Applicative[M]): ApplicativeOps[M, A] =
    new ApplicativeOps(a)

  implicit def toFunctionalBuilderOps[M[_], A](a: M[A])(
      implicit fcb: FunctionalCanBuild[M]
  ): FunctionalBuilderOps[M, A] = new FunctionalBuilderOps[M, A](a)(fcb)

  implicit def toMonoidOps[A](a: A)(implicit m: Monoid[A]): MonoidOps[A] = new MonoidOps(a)

  implicit def toFunctorOps[M[_], A](ma: M[A])(implicit fu: Functor[M]): FunctorOps[M, A] = new FunctorOps(ma)

  implicit def toContraFunctorOps[M[_], A](ma: M[A])(
      implicit fu: ContravariantFunctor[M]
  ): ContravariantFunctorOps[M, A] = new ContravariantFunctorOps(ma)

  implicit def toInvariantFunctorOps[M[_], A](ma: M[A])(implicit fu: InvariantFunctor[M]): InvariantFunctorOps[M, A] =
    new InvariantFunctorOps(ma)

  def unapply[B, A](f: B => Option[A]): B => A = { b: B =>
    f(b).get
  }

  def unlift[A, B](f: A => Option[B]): A => B = Function.unlift(f)

}
