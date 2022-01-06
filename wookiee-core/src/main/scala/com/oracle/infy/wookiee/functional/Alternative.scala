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

import scala.language.higherKinds

trait Alternative[M[_]] {

  def app: Applicative[M]
  def |[A, B >: A](alt1: M[A], alt2: M[B]): M[B]
  def empty: M[Nothing]
  //def some[A](m: M[A]): M[List[A]]
  //def many[A](m: M[A]): M[List[A]]

}

class AlternativeOps[M[_], A](alt1: M[A])(implicit a: Alternative[M]) {

  def |[B >: A](alt2: M[B]): M[B] = a.|(alt1, alt2)
  def or[B >: A](alt2: M[B]): M[B] = |(alt2)

}

