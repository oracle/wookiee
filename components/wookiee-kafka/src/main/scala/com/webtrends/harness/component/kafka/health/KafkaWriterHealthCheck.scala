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

package com.webtrends.harness.component.kafka.health

import java.util.concurrent.TimeUnit

import com.webtrends.harness.component.kafka.actor.KafkaWriter
import com.webtrends.harness.component.kafka.actor.KafkaWriter.EventToWrite
import com.webtrends.harness.health.ComponentState.ComponentState
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import org.apache.kafka.clients.producer.RecordMetadata
import scala.concurrent.{blocking, Future}
import scala.concurrent.duration._
import scala.util.Try

case object KafkaHealthRequest

trait KafkaWriterHealthCheck { this: KafkaWriter =>

  val healthName = "Kafka Writer"

  var currentHealth: Option[HealthComponent] = None

  var inProcessSend: Option[java.util.concurrent.Future[RecordMetadata]] = None

  // Amount of time we allow an async produce to run for before we consider it unhealthy
  // We need to allow the entire linger time since the production may not even start until this time has passed
  // plus some overhead for the production to actually occur.
  lazy val maxSendTimeMillis = Try { kafkaConfig.getConfig("producer").getInt("linger.ms") }.getOrElse(5000) +
    Try { kafkaConfig.getConfig("producer").getInt("produce-timeout-millis") }.getOrElse(10000)

  // Schedule a regular send to detect changes even if no data is flowing through
  val healthMessage = EventToWrite("producer_health", "Healthy".getBytes("utf-8"))
  val scheduledHealthProduce = context.system.scheduler.schedule(0 seconds, 5 seconds, self, healthMessage)(scala.concurrent.ExecutionContext.Implicits.global)

  def healthReceive: Receive = {
    case hc: HealthComponent =>
      currentHealth = Some(hc)

    case CheckHealth =>
      sender ! currentHealth.getOrElse(HealthComponent(healthName, ComponentState.NORMAL, "No data has been written yet"))
  }

  def setHealth(componentState: ComponentState, details: String) {
    currentHealth = Some(HealthComponent("Kafka Writer", componentState, details))
  }

  def stopHealthCheck: Unit = {
    scheduledHealthProduce.cancel()
    currentHealth = None
  }

  // The Kafka API makes it difficult to identify the target kafka server being down. A provided callback will not
  // be called and calling get on the returned java Future will block indefinitely.
  //
  // Addressing this by wrapping the returned java Future and into a scala Future and blocking with defined timeout.
  // If it completes successfully, and we don't have a normal health, set it to NORMAL
  // If it throws an exception or times out, set our health to CRITICAL
  //
  // To limit performance impact, we are only monitoring a single send at at ime
  def monitorSendHealth(sendFuture: java.util.concurrent.Future[RecordMetadata]) {
    inProcessSend match {
      case Some(f) => // Don't do anything, already monitoring a send
      case None =>
        inProcessSend = Some(sendFuture)
        Future {
          blocking {
            try {
              sendFuture.get(maxSendTimeMillis, TimeUnit.MILLISECONDS)
              if (currentHealth.isEmpty || currentHealth.get.state != ComponentState.NORMAL) {
                self ! HealthComponent(healthName, ComponentState.NORMAL, "Write successful")
              }
            }
            catch {
              case ex: Exception =>
                log.error("Unable to produce data. Marking producer as unhealthy", ex)
                self ! HealthComponent(healthName, ComponentState.CRITICAL, ex.getMessage)
            }
            inProcessSend = None // Note that this may be executed in a different thread than the current message being processed
          }
        }(scala.concurrent.ExecutionContext.Implicits.global)
    }
  }
}