package com.oracle.infy.wookiee.metrics.impl

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.TimeUnit

import cats.effect.IO
import com.codahale.metrics.{ConsoleReporter, MetricRegistry}
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.codahale.metrics.jmx.JmxReporter
import com.oracle.infy.wookiee.metrics.contract.WookieeMetrics
import com.oracle.infy.wookiee.metrics.model._

class WookieeMetricsImpl(settings: MetricsSettings) extends WookieeMetrics[IO] {

  val registry: MetricRegistry = new MetricRegistry()

  override def timer(name: String): IO[Timer] = Timer(name, registry)

  override def counter(name: String): IO[Counter] = Counter(name, registry)

  override def meter(name: String): IO[Meter] = Meter(name, registry)

  override def histogram(name: String, biased: Boolean): IO[Histogram] = Histogram(name, registry, biased)

  override def remove(name: String): IO[Boolean] = IO.delay(registry.remove(name))

  lazy val graphiteReporter: Option[GraphiteReporter] =
    if (settings.graphiteEnabled)
      Some(
        GraphiteReporter
          .forRegistry(registry)
          .prefixedWith(
            "%s.%s.%s".format(
              settings.metricPrefix,
              InetAddress.getLocalHost.getHostName.replace('.', '_'),
              settings.applicationName.replace(' ', '_').toLowerCase
            )
          )
          .build(new Graphite(new InetSocketAddress(settings.graphiteHost, settings.graphitePort)))
      )
    else None

  lazy val jmxReporter: Option[JmxReporter] = if (settings.jmxEnabled) Some(JmxReporter.forRegistry(registry).build) else None

  //TODO: to be removed (added for testing purpose)
  lazy val console: ConsoleReporter =
    ConsoleReporter
      .forRegistry(registry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()

  override def startReports(): IO[Unit] =
    for {
      _ <- IO(graphiteReporter.map(_.start(settings.graphiteInterval, TimeUnit.SECONDS)))
      _ <- IO(jmxReporter.map(_.start()))
      _ <- IO(console.start(3, TimeUnit.SECONDS))
      _ <- IO(println("Metrics service started.."))
    } yield ()

  override def stopReports(): IO[Unit] =
    for {
      _ <- IO(graphiteReporter.map(_.stop()))
      _ <- IO(jmxReporter.map(_.stop()))
    } yield ()

}
