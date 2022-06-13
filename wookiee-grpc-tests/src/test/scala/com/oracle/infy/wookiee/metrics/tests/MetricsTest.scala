package com.oracle.infy.wookiee.metrics.tests

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.codahale.metrics.MetricRegistry
import com.oracle.infy.wookiee.functional.metrics.core.WookieeMetrics
import com.oracle.infy.wookiee.functional.metrics.impl.WookieeMetricsImpl
import com.oracle.infy.wookiee.grpc.common.UTestScalaCheck
import com.oracle.infy.wookiee.grpc.utils.implicits._
import com.oracle.infy.wookiee.metrics.model.WookieeRegistry
import utest.{Tests, test}

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

object MetricsTest extends UTestScalaCheck {

  def tests()(implicit executionContext: ExecutionContext, runtime: IORuntime): Tests = {

    val registry = new MetricRegistry()
    val jvmRegistry = new MetricRegistry()
    val wookieeMetrics: WookieeMetrics[IO] = new WookieeMetricsImpl(WookieeRegistry(registry, jvmRegistry))
    val timerName = "timerTest"
    val counterName = "counterTest"
    val meterName = "meterTest"
    val histogramName = "histogramTest"
    val gaugeName = "gaugeTest"

    def timerCheck(): Future[Boolean] =
      (for {
        timer <- wookieeMetrics.timer(timerName)
        _ <- timer.update(1000, TimeUnit.NANOSECONDS)
        _ <- wookieeMetrics.time(timerName)(IO("test"))
      } yield {
        registry.getTimers.containsKey(timerName) && registry.timer(timerName).getCount === 2
      }).unsafeToFuture()

    def counterCheck(): Future[Boolean] =
      (for {
        counter <- wookieeMetrics.counter(counterName)
        _ <- counter.inc(2)

      } yield {
        registry.getCounters().containsKey(counterName) && registry.counter(counterName).getCount === 2
      }).unsafeToFuture()

    def meterCheck(): Future[Boolean] =
      (for {
        meter <- wookieeMetrics.meter(meterName)
        _ <- meter.mark()
      } yield {
        registry.getMeters.containsKey(meterName) && registry.meter(meterName).getCount === 1
      }).unsafeToFuture()

    def histogramCheck(): Future[Boolean] =
      (for {
        histogram <- wookieeMetrics.histogram(histogramName, biased = true)
        _ <- histogram.update(1)
      } yield {
        registry.getHistograms.containsKey(histogramName) && registry.histogram(histogramName).getCount === 1
      }).unsafeToFuture()

    def gaugeCheck(): Future[Boolean] =
      (for {
        _ <- wookieeMetrics.gauge[String](gaugeName)
      } yield {
        registry.getGauges.containsKey(gaugeName)
      }).unsafeToFuture()

    def removeMetricCheck(): Future[Boolean] =
      (for {
        timer <- wookieeMetrics.timer("removeTest")
        _ <- timer.update(1000, TimeUnit.NANOSECONDS)
        isRemoved <- wookieeMetrics.remove("removeTest")
      } yield {
        isRemoved && registry.getTimers.containsKey("removeTest") === false

      }).unsafeToFuture()

    def getMetricsCheck: Future[Boolean] =
      (for {
        timer <- wookieeMetrics.timer("timer")
        _ <- timer.update(1000, TimeUnit.NANOSECONDS)
        counter <- wookieeMetrics.counter("counter")
        _ <- counter.inc(2)
        json <- wookieeMetrics.getMetrics
      } yield {
        val metricsJson = json.hcursor.downField("system").downField("metrics")
        val timerCount = metricsJson.downField("timer").downField("rate").downField("count").as[Int]
        val counterCount = metricsJson.downField("counter").as[Int]
        timerCount === Right(1) && counterCount === Right(2)
      }).unsafeToFuture()

    Tests {
      test("able register timer and record it") {
        timerCheck().map(assert)
      }
      test("able register counter and record it") {
        counterCheck().map(assert)
      }
      test("able register meter and record it") {
        meterCheck().map(assert)
      }
      test("able register histogram and record it") {
        histogramCheck().map(assert)
      }
      test("able register gauge and record it") {
        gaugeCheck().map(assert)
      }
      test("able to remove already registed metrics") {
        removeMetricCheck().map(assert)
      }
      test("able to get metrics details in json format") {
        getMetricsCheck.map(assert)
      }

    }

  }
}
