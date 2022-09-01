/*
 * Copyright 2015 Oracle (http://www.Oracle.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oracle.infy.wookiee.component.metrics

import akka.actor.{Actor, Props}
import com.codahale.metrics.graphite.GraphiteReporter.Builder
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.codahale.metrics.jmx.JmxReporter
import com.codahale.metrics.{MetricAttribute, MetricFilter, ScheduledReporter}
import com.oracle.infy.wookiee.component.messages.StatusRequest
import com.oracle.infy.wookiee.component.metrics.messages._
import com.oracle.infy.wookiee.component.metrics.monitoring.MonitoringSettings
import com.oracle.infy.wookiee.health.{ActorHealth, ComponentState, HealthComponent}
import com.oracle.infy.wookiee.logging.ActorLoggingAdapter
import org.json4s.jackson.JsonMethods._

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
  * @author Michael Cuthbert on 11/19/14.
  */
object MetricsActor {
  def props(settings: MonitoringSettings): Props = Props(new MetricsActor(settings))

  var health: HealthComponent = HealthComponent(Metrics.MetricsName, ComponentState.NORMAL, "Metrics not started yet.")
}

class MetricsActor(settings: MonitoringSettings) extends Actor with ActorLoggingAdapter with ActorHealth {

  private[metrics] var jmxReporter: Option[JmxReporter] = None
  private[metrics] var graphiteReporter: Option[GraphiteReporter] = None
  private[metrics] var jvmGraphiteReporter: Option[GraphiteReporter] = None

  def receive: Receive = health orElse {
    case StatusRequest(format) =>
      val metrics = new MetricsWriter().getMetrics()
      format match {
        case "string" =>
          log.debug("Fetching the metrics in string format")
          sender() ! compact(render(metrics))
        case _ =>
          log.debug("Fetching the metrics in json format")
          sender() ! metrics
      }

    case RemoveMetric(metric) => MetricBuilder.remove(metric); ()

    case o: MetricObservation =>
      o match {
        case o: CounterObservation =>
          MetricBuilder(o).inc(o.delta)
        case o: GaugeObservation =>
          MetricBuilder(o).setValue(o.value)
        case o: HistogramObservation =>
          MetricBuilder(o).update(o.value)
        case o: MeterObservation =>
          MetricBuilder(o).mark(o.events)
        case o: TimerObservation =>
          MetricBuilder(o).update(o.elapsed, o.unit)
      }
    case _ => // Just eat it
  }

  override def preStart(): Unit = {
    // Subscribe to events
    MetricsEventBus.subscribe(self)

    if (settings.GraphiteEnabled) {
      val graphiteBuilder: Builder = GraphiteReporter
        .forRegistry(MetricBuilder())
        .prefixedWith(
          "%s.%s.%s".format(
            settings.MetricPrefix,
            InetAddress.getLocalHost.getHostName.replace('.', '_'),
            settings.ApplicationName.replace(' ', '_').toLowerCase
          )
        )

      settings.GraphiteDisabledMetricAttributes match {
        case Some(attributes) => graphiteBuilder.disabledMetricAttributes(attributes.map(MetricAttribute.valueOf).asJava)
                    System.out.println(attributes)
        case None =>
          System.out.println("none")
      }

      graphiteReporter = Some(
        graphiteBuilder.build(new Graphite(new InetSocketAddress(settings.GraphiteHost, settings.GraphitePort)))
      )

      jvmGraphiteReporter = Some(
        GraphiteReporter
          .forRegistry(MetricBuilder.jvmRegistry)
          .prefixedWith(
            "%s.%s.%s.jvm".format(
              settings.MetricPrefix,
              InetAddress.getLocalHost.getHostName.replace('.', '_'),
              settings.ApplicationName.replace(' ', '_').toLowerCase
            )
          )
          .build(new Graphite(new InetSocketAddress(settings.GraphiteHost, settings.GraphitePort)))
      )

      graphiteReporter.get.start(settings.GraphiteInterval, TimeUnit.MINUTES)
      jvmGraphiteReporter.get.start(settings.GraphiteInterval, TimeUnit.MINUTES)
    }

    // Start the JMX engine
    MetricBuilder.registerJvmMetrics

    if (settings.JmxEnabled) {
      jmxReporter = Some(JmxReporter.forRegistry(MetricBuilder()).build)
      jmxReporter.get.start()
    }

    setHealth()
    log.info("Metrics Manager started: {}", context.self.path)
  }

  override def postStop(): Unit = {
    // Un-subscribe to events
    MetricsEventBus.unsubscribe(self)

    graphiteReporter match {
      case Some(reporter) =>
        // Flush our data
        reporter.asInstanceOf[ScheduledReporter].report()
        reporter.close()
        graphiteReporter = None
      case _ =>
      // do nothing
    }
    jvmGraphiteReporter match {
      case Some(reporter) =>
        // Flush our data
        reporter.asInstanceOf[ScheduledReporter].report()
        reporter.close()
        jvmGraphiteReporter = None
      case _ =>
      // do nothing
    }

    jmxReporter match {
      case Some(reporter) =>
        reporter.stop()
        jmxReporter = None
      case _ =>
      // do nothing
    }

    MetricBuilder.registry.removeMatching(MetricFilter.ALL)
    MetricBuilder.jvmRegistry.removeMatching(MetricFilter.ALL)
    log.info("Metrics Manager stopped: {}", context.self.path)
  }

  protected def setHealth(): Unit = {
    MetricsActor.health = Try({
      if (settings.GraphiteEnabled) {
        HealthComponent(
          Metrics.MetricsName,
          ComponentState.NORMAL,
          "Currently sending metrics to graphite at %s:%d"
            .format(settings.GraphiteHost, settings.GraphitePort)
        )
      } else HealthComponent(Metrics.MetricsName, ComponentState.NORMAL, "Currently not sending metrics to graphite")
    }).recover({
        case e: Exception =>
          HealthComponent(
            Metrics.MetricsName,
            ComponentState.CRITICAL,
            "An error occurred checking the metrics health: ".concat(e.getMessage)
          )
      })
      .get
  }
}
