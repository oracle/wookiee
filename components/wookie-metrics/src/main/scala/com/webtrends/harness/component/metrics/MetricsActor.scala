/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
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

package com.webtrends.harness.component.metrics

import java.net.{InetSocketAddress, InetAddress}
import java.util.concurrent.TimeUnit

import akka.actor.{Props, Actor}
import com.codahale.metrics.{MetricFilter, ScheduledReporter, JmxReporter}
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.webtrends.harness.component.messages.{GetMetric, StatusRequest}
import com.webtrends.harness.component.metrics.messages._
import com.webtrends.harness.component.metrics.monitoring.MonitoringSettings
import com.webtrends.harness.health.{ComponentState, HealthComponent, ActorHealth}
import com.webtrends.harness.logging.ActorLoggingAdapter
import net.liftweb.json._

import scala.concurrent.Future
import scala.util.Try

/**
 * @author Michael Cuthbert on 11/19/14.
 */
object MetricsActor {
  def props(settings:MonitoringSettings): Props = Props(classOf[MetricsActor], settings)
}

class MetricsActor(settings:MonitoringSettings) extends Actor with ActorLoggingAdapter with ActorHealth {
  import context.dispatcher

  private[metrics] var jmxReporter: Option[JmxReporter] = None
  private[metrics] var graphiteReporter: Option[GraphiteReporter] = None
  private[metrics] var jvmGraphiteReporter: Option[GraphiteReporter] = None

  def receive = health orElse {
    case StatusRequest(format) =>
      val metrics = new MetricsWriter().getMetrics(settings.GraphiteIncludeVMMetrics)
      format match {
        case "string" =>
          log.debug("Fetching the metrics in string format")
          sender ! compact(render(metrics))
        case _ =>
          log.debug("Fetching the metrics in json format")
          sender ! metrics
      }

    case GetMetric(name) =>
      log.debug(s"Fetching metric $name")
      val reg = MetricBuilder.registry
      val i = 0

    case RemoveMetric(metric) => MetricBuilder.remove(metric)

    case o: MetricObservation => o match {
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

  override def preStart() {
    // Subscribe to events
    MetricsEventBus.subscribe(self)

    if (settings.GraphiteEnabled) {
      graphiteReporter = Some(GraphiteReporter.forRegistry(MetricBuilder())
        .prefixedWith("%s.%s.%s".format(settings.MetricPrefix,
        InetAddress.getLocalHost.getHostName.split("\\.")(0),
        settings.ApplicationName.replace(' ', '_').toLowerCase))
        .build(new Graphite(new InetSocketAddress(settings.GraphiteHost,
        settings.GraphitePort))))

      jvmGraphiteReporter = Some(GraphiteReporter.forRegistry(MetricBuilder.jvmRegistry)
        .prefixedWith("%s.%s.%s.jvm".format(settings.MetricPrefix,
        InetAddress.getLocalHost.getHostName.split("\\.")(0),
        settings.ApplicationName.replace(' ', '_').toLowerCase))
        .build(new Graphite(new InetSocketAddress(settings.GraphiteHost,
        settings.GraphitePort))))

      graphiteReporter.get.start(settings.GraphiteInterval, TimeUnit.MINUTES)
      jvmGraphiteReporter.get.start(settings.GraphiteInterval, TimeUnit.MINUTES)
    }

    // Start the JMX engine
    MetricBuilder.registerJvmMetrics

    if (settings.JmxEnabled) {
      jmxReporter = Some(JmxReporter.forRegistry(MetricBuilder()).build)
      jmxReporter.get.start
    }

    log.info("Metrics manager started: {}", context.self.path)
  }

  override def postStop() = {
    // Un-subscribe to events
    MetricsEventBus.unsubscribe(self)

    graphiteReporter match {
      case Some(reporter) =>
        // Flush our data
        reporter.asInstanceOf[ScheduledReporter].report
        reporter.close
        graphiteReporter = None
      case _ =>
      // do nothing
    }
    jvmGraphiteReporter match {
      case Some(reporter) =>
        // Flush our data
        reporter.asInstanceOf[ScheduledReporter].report
        reporter.close
        jvmGraphiteReporter = None
      case _ =>
      // do nothing
    }

    jmxReporter match {
      case Some(reporter) =>
        reporter.stop
        jmxReporter = None
      case _ =>
      // do nothing
    }

    MetricBuilder.registry.removeMatching(MetricFilter.ALL)
    MetricBuilder.jvmRegistry.removeMatching(MetricFilter.ALL)
    log.info("Metrics manager stopped: {}", context.self.path)
  }

  /**
   * This is the health of the current object, by default will be NORMAL
   * In general this should be overridden to define the health of the current object
   * For objects that simply manage other objects you shouldn't need to do anything
   * else, as the health of the children components would be handled by their own
   * CheckHealth function
   *
   * @return
   */
  override protected def getHealth: Future[HealthComponent] = {
    Future {
      Try({
        log.debug("MetricsActor health requested")
        settings.GraphiteEnabled match {
          case true =>
            HealthComponent(Metrics.MetricsName, ComponentState.NORMAL, "Currently sending metrics to graphite at %s:%d"
              .format(settings.GraphiteHost, settings.GraphitePort))
          case false =>
            HealthComponent(Metrics.MetricsName, ComponentState.NORMAL, "Currently not sending metrics to graphite")
        }
      }).recover({
        case e: Exception => HealthComponent(Metrics.MetricsName, ComponentState.CRITICAL, "An error occurred checking the metrics health: ".concat(e.getMessage))
      }).get
    }
  }
}
