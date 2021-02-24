package com.oracle.infy.wookiee.metrics

import java.lang.management.ManagementFactory

import cats.effect.IO
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jvm._
import com.oracle.infy.wookiee.metrics.core.{WookieeMetrics, WookieeMetricsReporter}
import com.oracle.infy.wookiee.metrics.impl.{WookieeMetricsImpl, WookieeMetricsNoOpImpl}
import com.oracle.infy.wookiee.metrics.model.WookieeRegistry

import scala.jdk.CollectionConverters._

object WookieeMetricsService {

  def register(report: WookieeRegistry => IO[WookieeMetricsReporter[IO]]): IO[WookieeMetrics[IO]] = {
    for {
      registry <- IO.delay(new MetricRegistry())
      jvmRegistry <- IO.delay(new MetricRegistry())
      _ <- registerJvmMetrics(jvmRegistry)
      r <- report(WookieeRegistry(registry, jvmRegistry))
      _ <- r.start()
    } yield new WookieeMetricsImpl(WookieeRegistry(registry, jvmRegistry), r)
  }

  def noOpRegister(): IO[WookieeMetrics[IO]] = IO(new WookieeMetricsNoOpImpl())

  private def registerJvmMetrics(jvmRegistry: MetricRegistry): IO[Unit] =
    IO.delay {
      {
        val gc = new GarbageCollectorMetricSet()
        val bufferPoolMetricSet = new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer)
        val memoryUsage = new MemoryUsageGaugeSet()
        val threadStates = new ThreadStatesGaugeSet()

        jvmRegistry.register("gc", gc)
        jvmRegistry.register("buffer-pool", bufferPoolMetricSet)
        jvmRegistry.register("memory", memoryUsage)
        jvmRegistry.register("thread", threadStates)

        //Rename gc.time metrics to avoid issues with InfluxDB using time as a reserved word
        jvmRegistry.getGauges.asScala.iterator.foreach {
          case (name, gauge) =>
            val index = name.lastIndexOf(".time")
            if (index > 0) {
              jvmRegistry.remove(name)
              jvmRegistry.register(s"${name.substring(0, index)}.gctime", gauge)
            }
        }
      }
    }

}
