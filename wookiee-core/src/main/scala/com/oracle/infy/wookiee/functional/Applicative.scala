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
package com.oracle.infy.wookiee.functional

trait Applicative[M[_]] {

  def pure[A](a: A): M[A]
  def map[A, B](m: M[A], f: A => B): M[B]
  def apply[A, B](mf: M[A => B], ma: M[A]): M[B]

}

object Applicative {

  implicit val applicativeOption: Applicative[Option] = new Applicative[Option] {

    def pure[A](a: A): Option[A] = Some(a)

    def map[A, B](m: Option[A], f: A => B): Option[B] = m.map(f)

    def apply[A, B](mf: Option[A => B], ma: Option[A]): Option[B] = mf.flatMap(f => ma.map(f))

  }

}

class ApplicativeOps[M[_], A](ma: M[A])(implicit a: Applicative[M]) {

  def ~>[B](mb: M[B]): M[B] = a(a(a.pure((_: A) => (b: B) => b), ma), mb)
  def andKeep[B](mb: M[B]): M[B] = ~>(mb)

  def <~[B](mb: M[B]): M[A] = a(a(a.pure((a: A) => (_: B) => a), ma), mb)
  def keepAnd[B](mb: M[B]): M[A] = <~(mb)

  def <~>[B, C](mb: M[B])(implicit witness: <:<[A, B => C]): M[C] = apply(mb)
  def apply[B, C](mb: M[B])(implicit witness: <:<[A, B => C]): M[C] = a(a.map(ma, witness), mb)
}
