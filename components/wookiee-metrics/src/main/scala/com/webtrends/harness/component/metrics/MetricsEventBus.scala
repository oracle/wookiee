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

import akka.actor.ActorRef
import akka.event.{ActorEventBus, ScanningClassification}
import com.webtrends.harness.component.metrics.messages.MetricMessage

class MetricsEventBus extends ActorEventBus with ScanningClassification {

  type Classifier = Boolean
  type Event = MetricMessage

  /**
   * Provides a total ordering of Classifiers (think java.util.Comparator.compare)
   */
  protected def compareClassifiers(a: Classifier, b: Classifier): Int = 0

  /**
   * Returns whether the specified Classifier matches the specified Event
   */
  protected def matches(classifier: Classifier, event: Event): Boolean = true

  /**
   * Publishes the given Event to the given Subscriber
   */
  protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event
}

object MetricsEventBus {
  /**
   * The single instance of the bus
   */
  private lazy val bus = new MetricsEventBus

  /**
   * Subscribe for metric events
   */
  private[harness] def subscribe(subscriber: ActorRef): Unit = bus.subscribe(subscriber, true)

  /**
   * Unsubscribe for metric events
   */
  private[harness] def unsubscribe(subscriber: ActorRef): Unit = bus.unsubscribe(subscriber)

  /**
   * Publish the metric message
   * @param message the metric message to publish
   */
  private[metrics] def publish(message: MetricMessage) = bus.publish(message)

}
