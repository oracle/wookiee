package com.oracle.infy.wookiee.metrics.core

trait WookieeMetricsReporter[F[_]] {

  def start(): F[Unit]
  def stop(): F[Unit]

}
