package com.oracle.infy.wookiee.functional.metrics.core

import com.oracle.infy.wookiee.metrics.model._
import io.circe.Json

trait WookieeMetrics[F[_]] {

  def timer(name: String): F[Timer[F]]

  def time[A](name: String)(inner: F[A]): F[A]

  def counter(name: String): F[Counter[F]]

  def meter(name: String): F[Meter[F]]

  def histogram(name: String, biased: Boolean): F[Histogram[F]]

  def gauge[A](name: String): F[Gauge[F, A]]

  def remove(name: String): F[Boolean]

  def getMetrics: F[Json]

}
