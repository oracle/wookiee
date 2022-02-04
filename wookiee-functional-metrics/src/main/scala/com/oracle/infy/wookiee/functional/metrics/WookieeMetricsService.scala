package com.oracle.infy.wookiee.functional.metrics

import cats.effect.{IO, Resource}
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jvm._
import com.oracle.infy.wookiee.functional.metrics.core.{WookieeMetrics, WookieeMetricsReporter}
import com.oracle.infy.wookiee.functional.metrics.impl.{WookieeMetricsImpl, WookieeMetricsNoOpImpl, WookieeMetricsReporterNoOpImpl}
import com.oracle.infy.wookiee.metrics.model.WookieeRegistry

import java.lang.management.ManagementFactory
import scala.jdk.CollectionConverters._

object WookieeMetricsService {

  def register(
      metricRegistry: MetricRegistry,
      reporter: WookieeRegistry => IO[WookieeMetricsReporter[IO]]
  ): Resource[IO, WookieeMetrics[IO]] =
    Resource
      .make {
        for {
          jvmRegistry <- IO(new MetricRegistry())
          _ <- registerJvmMetrics(jvmRegistry)
          r <- reporter(WookieeRegistry(metricRegistry, jvmRegistry))
          _ <- r.report()
        } yield (new WookieeMetricsImpl(WookieeRegistry(metricRegistry, jvmRegistry)), r)
      } { case (_, reporter) => reporter.stop() }
      .map(_._1)

  def register(metricRegistry: MetricRegistry): Resource[IO, WookieeMetrics[IO]] = {
    val noOp = IO(new WookieeMetricsReporterNoOpImpl)
    register(metricRegistry, _ => noOp)
  }

  def register(reporter: WookieeRegistry => IO[WookieeMetricsReporter[IO]]): Resource[IO, WookieeMetrics[IO]] =
    register(new MetricRegistry(), reporter)

  def noOpRegister(): Resource[IO, WookieeMetrics[IO]] = Resource.eval(IO(new WookieeMetricsNoOpImpl()))

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
