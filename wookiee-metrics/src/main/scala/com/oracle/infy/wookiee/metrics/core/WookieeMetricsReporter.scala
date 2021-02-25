package com.oracle.infy.wookiee.metrics.core

trait WookieeMetricsReporter[F[_]] {

  def report(): F[Unit]
  def stop(): F[Unit]

}
