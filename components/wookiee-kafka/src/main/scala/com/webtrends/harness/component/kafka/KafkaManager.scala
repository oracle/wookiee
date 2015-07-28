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

package com.webtrends.harness.component.kafka

import akka.actor.{ActorRef, Props}
import akka.pattern._
import com.webtrends.harness.app.HarnessActor.{ConfigChange, PrepareForShutdown, SystemReady}
import com.webtrends.harness.component.Component
import com.webtrends.harness.component.kafka.actor.{SourceMonitor, KafkaTopicManager, KafkaWriter}
import com.webtrends.harness.component.kafka.util.KafkaSettings
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth

import scala.concurrent.{Future, Promise}
import scala.util.{Try, Failure, Success}

/**
 * This class manages the creation of both the KafkaConsumerCoordinator (if 'consumer' is configured)
 * and the KafkaWriter (if 'producer' is configured). One can retrieve either of these actors by
 * making a call to GetCoordinator or GetWriters
 */
object KafkaManager {
  // User this to retrieve the coordinator in charge of managing Workers
  case object GetCoordinator
  //Will return the distributor
  case object GetDistributor
  // Kafka producer started up if 'producer' is configured
  var producer: Option[ActorRef] = None
}

class KafkaManager(name: String) extends Component(name) with KafkaSettings {
  import KafkaManager._
  import context.dispatcher
  // Consumer coordinator started up if 'consumer' is configured
  var coordinator: Option[ActorRef] = None

  // Source monitor started if source-monitor = true
  var sourceMonitor: Option[ActorRef] = None

  // Consumer proxy, started up if 'consumer' is configured. Used to retrieve topic
  // information and the data to consume
  var consumerManager: Option[ActorRef] = None

  //Distributor actor will start up if a 'consumer' is configured
  var distributor: Option[ActorRef] = None

  var consumerManagerHealth: Option[HealthComponent] = None

  // Start up the producer as soon as we can since it has no dependencies
  override def start = {
    if (kafkaConfig.hasPath("producer")) {
      startProducer()
    }
    super.start
  }

  override def receive = super.receive orElse configReceive orElse {
    // Use this call to get the Kafka Consumer Coordinator, if configured
    case GetCoordinator =>
      sender ! coordinator

    // We are loading the coordinator here because it depends on zookeeper component being loaded up which
    // will have occurred earlier at the ComponentStart phase
    case SystemReady =>
      if (kafkaConfig.hasPath("consumer")) {
        startCoordinator()
      }

    case GetDistributor =>
      sender ! distributor

    case PrepareForShutdown =>
      coordinator.foreach(_ ! PrepareForShutdown)
      distributor.foreach(_ ! PrepareForShutdown)

    case h: HealthComponent =>
      consumerManagerHealth = Some(h)
  }

  def startProducer() {
    log.info("Starting producer as wookiee-kafka config contained 'producer' config")
    producer = Some(context.system.actorOf(Props[KafkaWriter], "producer"))
  }

  override def checkHealth: Future[HealthComponent] = {
    val p = Promise[HealthComponent]()
    val children = Seq(distributor, coordinator, producer)

    getHealth.onComplete {
      case Success(s) =>
        val healthFutures = children.flatten map { ref =>
          (ref ? CheckHealth).mapTo[HealthComponent] recover {
            case ex: Exception => HealthComponent(ref.path.name, ComponentState.CRITICAL, s"Failure to get health of child component. ${ex.getMessage}")
          }
        }

        Future.sequence(healthFutures) onComplete {
          case Failure(f) =>
            log.debug(f, "Failed to retrieve health of children objects")
            p success HealthComponent(s.name, ComponentState.CRITICAL, s"Failure to get health of child components. ${f.getMessage}")
          case Success(healths) =>
            healths foreach { it => s.addComponent(it) }
            p success s
        }
      case Failure(f) =>
        log.debug(f, "Failed to get health from component")
        p success HealthComponent(self.path.toString, ComponentState.CRITICAL, f.getMessage)
    }

    p.future
  }

  override protected def getHealth: Future[HealthComponent] = {
    val h = HealthComponent(self.path.toString, ComponentState.NORMAL, "Healthy")
    consumerManagerHealth.foreach(h.addComponent)
    Future.successful(h)
  }

  /**
   * Start up all necessary actors
   */
  def startCoordinator() {
    if(coordinator.isEmpty) {
      log.info(s"Starting coordinator class")
      if (Try { kafkaConfig.getBoolean("monitor-sources") } getOrElse false) {
        log.info("'monitor-sources' is true, starting Source Monitor")
        sourceMonitor = Some(context.actorOf(Props(classOf[SourceMonitor]), "source-monitor"))
      }
      consumerManager = Some(context.actorOf(KafkaTopicManager.props(sourceMonitor), "consumer-manager"))
      coordinator = Some(context.actorOf(Props(leader, consumerManager.get, sourceMonitor), "consumer-coordinator"))
      distributor = Some(context.actorOf(KafkaConsumerDistributor.props(consumerManager.get), "consumer-distributor"))
    }
  }

  override def renewConfiguration() {
    log.info("Received config change message, checking hosts for changes...")
    super.renewConfiguration()
    if (coordinator.isDefined) {
      coordinator.get ! ConfigChange()
    }
  }

  override def postStop() {
    stop
  }

  // Stops the coordinator, unregistering this node from zookeeper
  override def stop {
    context.children foreach { child ⇒
      log.info(s"Stopping child [${child.path}]")
      context.stop(child)
    }
    coordinator = None
    consumerManager = None
    distributor = None
    producer = None
  }
}

object Kafka {
  val ComponentName = "wookiee-kafka"
}
