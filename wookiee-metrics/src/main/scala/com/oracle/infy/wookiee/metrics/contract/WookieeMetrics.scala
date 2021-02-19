package com.oracle.infy.wookiee.metrics.contract

import com.oracle.infy.wookiee.metrics.model.{Counter, Histogram, Meter, Timer}

trait WookieeMetrics[F[_]] {

  def timer(name: String): F[Timer]

  def counter(name: String): F[Counter]

  def meter(name: String): F[Meter]

  def histogram(name: String, biased: Boolean): F[Histogram]

  def remove(name: String): F[Boolean]

  def startReports(): F[Unit]

  def stopReports(): F[Unit]

}
