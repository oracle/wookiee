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

sealed trait Variant[M[_]]

trait Functor[M[_]] extends Variant[M] {

  def fmap[A, B](m: M[A], f: A => B): M[B]

}

object Functor {

  implicit val functorOption: Functor[Option] = new Functor[Option] {
    def fmap[A, B](a: Option[A], f: A => B): Option[B] = a.map(f)
  }

}

trait InvariantFunctor[M[_]] extends Variant[M] {

  def inmap[A, B](m: M[A], f1: A => B, f2: B => A): M[B]

}

trait ContravariantFunctor[M[_]] extends Variant[M] {

  def contramap[A, B](m: M[A], f1: B => A): M[B]

}

class FunctorOps[M[_], A](ma: M[A])(implicit fu: Functor[M]) {

  def fmap[B](f: A => B): M[B] = fu.fmap(ma, f)

}

class ContravariantFunctorOps[M[_], A](ma: M[A])(implicit fu: ContravariantFunctor[M]) {

  def contramap[B](f: B => A): M[B] = fu.contramap(ma, f)

}

class InvariantFunctorOps[M[_], A](ma: M[A])(implicit fu: InvariantFunctor[M]) {

  def inmap[B](f: A => B, g: B => A): M[B] = fu.inmap(ma, f, g)

}

// Work around the fact that Scala does not support higher-kinded type patterns (type variables can only be simple identifiers)
// We use case classes wrappers so we can pattern match using their extractor
sealed trait VariantExtractor[M[_]]

case class FunctorExtractor[M[_]](functor: Functor[M]) extends VariantExtractor[M]

case class InvariantFunctorExtractor[M[_]](InvariantFunctor: InvariantFunctor[M]) extends VariantExtractor[M]

case class ContravariantFunctorExtractor[M[_]](ContraVariantFunctor: ContravariantFunctor[M])
    extends VariantExtractor[M]

object VariantExtractor {

  implicit def functor[M[_]: Functor]: FunctorExtractor[M] =
    FunctorExtractor(implicitly[Functor[M]])

  implicit def contravariantFunctor[M[_]: ContravariantFunctor]: ContravariantFunctorExtractor[M] =
    ContravariantFunctorExtractor(implicitly[ContravariantFunctor[M]])

  implicit def invariantFunctor[M[_]: InvariantFunctor]: InvariantFunctorExtractor[M] =
    InvariantFunctorExtractor(implicitly[InvariantFunctor[M]])

}
