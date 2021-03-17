package com.oracle.infy.wookiee.metrics.impl

import cats.effect.IO
import com.oracle.infy.wookiee.metrics.core.WookieeMetricsReporter

class WookieeMetricsReporterNoOpImpl extends WookieeMetricsReporter[IO] {
  def report(): IO[Unit] = IO.unit
  def stop(): IO[Unit] = IO.unit
}
