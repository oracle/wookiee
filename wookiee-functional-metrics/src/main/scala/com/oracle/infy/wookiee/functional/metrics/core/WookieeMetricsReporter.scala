package com.oracle.infy.wookiee.functional.metrics.core

trait WookieeMetricsReporter[F[_]] {

  def report(): F[Unit]
  def stop(): F[Unit]

}
