package com.oracle.infy.wookiee.metrics.core

import com.oracle.infy.wookiee.metrics.model.{Counter, Gauge, Histogram, Meter, Timer}
import io.circe.Json

trait WookieeMetrics[F[_]] {

  def timer(name: String): F[Timer]

  def time[A](name: String)(inner: F[A]): F[A]

  def counter(name: String): F[Counter]

  def meter(name: String): F[Meter]

  def histogram(name: String, biased: Boolean): F[Histogram]

  def gauge[A](name: String, f: () => A): F[Gauge[A]]

  def remove(name: String): F[Boolean]

  def getMetrics: F[Json]

}
