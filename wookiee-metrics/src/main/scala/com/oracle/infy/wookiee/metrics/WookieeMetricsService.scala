package com.oracle.infy.wookiee.metrics

import cats.effect.IO
import com.oracle.infy.wookiee.metrics.contract.WookieeMetrics
import com.oracle.infy.wookiee.metrics.impl.{WookieeMetricsImpl, WookieeMetricsNoOpImpl}
import com.oracle.infy.wookiee.metrics.model._

object WookieeMetricsService {

  def register(settings: MetricsSettings): WookieeMetrics[IO] = new WookieeMetricsImpl(settings)

  def noOpRegister(): WookieeMetrics[IO] = new WookieeMetricsNoOpImpl()

}
