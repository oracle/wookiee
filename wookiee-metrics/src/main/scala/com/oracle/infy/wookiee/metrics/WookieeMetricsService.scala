package com.oracle.infy.wookiee.metrics

import java.lang.management.ManagementFactory

import cats.effect.{IO, Resource}
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jvm._
import com.oracle.infy.wookiee.metrics.core.{WookieeMetrics, WookieeMetricsReporter}
import com.oracle.infy.wookiee.metrics.impl.{WookieeMetricsImpl, WookieeMetricsNoOpImpl}
import com.oracle.infy.wookiee.metrics.model.WookieeRegistry

import scala.jdk.CollectionConverters._

object WookieeMetricsService {

  def register(reporter: WookieeRegistry => IO[WookieeMetricsReporter[IO]]): Resource[IO, WookieeMetrics[IO]] = {
    Resource
      .make {
        for {
          metricRegistry <- IO(new MetricRegistry())
          jvmRegistry <- IO(new MetricRegistry())
          _ <- registerJvmMetrics(jvmRegistry)
          r <- reporter(WookieeRegistry(metricRegistry, jvmRegistry))
          _ <- r.report()
        } yield (new WookieeMetricsImpl(WookieeRegistry(metricRegistry, jvmRegistry)), r)
      } { case (_, reporter) => reporter.stop() }
      .map(_._1)
  }

  def noOpRegister(): Resource[IO, WookieeMetrics[IO]] = Resource.liftF(IO(new WookieeMetricsNoOpImpl()))

  private def registerJvmMetrics(jvmRegistry: MetricRegistry): IO[Unit] =
    IO {
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
