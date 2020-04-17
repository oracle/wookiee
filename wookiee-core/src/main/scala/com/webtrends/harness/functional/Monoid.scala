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
package com.webtrends.harness.functional

trait Monoid[A] {

  def append(a1: A, a2: A): A
  def identity: A

}

object Monoid {
  implicit def endomorphismMonoid[A]: Monoid[A => A] = new Monoid[A => A] {
    override def append(f1: A => A, f2: A => A) = f2 compose f1
    override def identity = Predef.identity
  }
}

class MonoidOps[A](m1: A)(implicit m: Monoid[A]) {
  def |+|(m2: A): A = m.append(m1, m2)
}

/* A practical variant of monoid act/action/operator (search on wikipedia)
 * - allows to take an element A to create a B
 * - allows a prepend/append a A to a B
 * cf Reducer[JsValue, JsArray]
 */
trait Reducer[A, B] {

  def unit(a: A): B
  def prepend(a: A, b: B): B
  def append(b: B, a: A): B

}

object Reducer {
  def apply[A, B](f: A => B)(implicit m: Monoid[B]) = new Reducer[A, B] {
    def unit(a: A): B = f(a)
    def prepend(a: A, b: B) = m.append(unit(a), b)
    def append(b: B, a: A) = m.append(b, unit(a))
  }
}
