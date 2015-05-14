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
import akka.pattern.ask
import com.webtrends.harness.app.HarnessActor.{ConfigChange, PrepareForShutdown, SystemReady}
import com.webtrends.harness.component.Component
import com.webtrends.harness.component.kafka.actor.{KafkaConsumerProxy, KafkaProducer}
import com.webtrends.harness.component.kafka.util.KafkaSettings
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

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

  // Consumer coordinator started up if 'consumer' is configured
  var coordinator: Option[ActorRef] = None

  // Consumer proxy, started up if 'consumer' is configured. Used to retrieve topic
  // information and the data to consume
  var proxy: Option[ActorRef] = None

  //Distributor actor will start up if a 'consumer' is configured
  var distributor: Option[ActorRef] = None


  override def receive = super.receive orElse configReceive orElse {
    // Use this call to get the Kafka Consumer Coordinator, if configured
    case GetCoordinator =>
      sender ! coordinator

    case GetDistributor =>
      sender ! distributor

    case SystemReady =>
      if (kafkaConfig.hasPath("producer")) {
        startProducer()
      }
      if (kafkaConfig.hasPath("consumer")) {
        startCoordinator()
      }
    case PrepareForShutdown =>
      coordinator.foreach(_ ! PrepareForShutdown)
      distributor.foreach(_ ! PrepareForShutdown)
  }

  def startProducer() {
    log.info("Starting producer as wookie-kafka config contained 'producer' config")
    producer = Some(context.actorOf(KafkaProducer.props(), "producer"))
  }

  /**
   * Start up all necessary actors
   */
  def startCoordinator() {
    if(coordinator.isEmpty) {
      log.info(s"Starting coordinator class")
      proxy = Some(context.actorOf(KafkaConsumerProxy.props(), "consumer-proxy"))
      coordinator = Some(context.actorOf(Props(leader, proxy.get), "consumer-coordinator"))
      distributor = Some(context.actorOf(KafkaConsumerDistributor.props(proxy.get), "consumer-distributor"))
    }
  }

  override def renewConfiguration() {
    log.info("Received config change message, checking hosts for changes...")
    super.renewConfiguration()
    if (coordinator.isDefined) {
      coordinator.get ! ConfigChange()
    }
  }

  override def checkHealth: Future[HealthComponent] = {
    val p = Promise[HealthComponent]()
    val health = HealthComponent("wookie-kafka", ComponentState.NORMAL, "Kafka ready to process")
    val healthFutures = List(proxy, coordinator, distributor, producer).flatten map { ref =>
      (ref ? CheckHealth).mapTo[HealthComponent] recover {
        case ex: Exception => HealthComponent(ref.path.name, ComponentState.CRITICAL, s"Failure to get health of child component. ${ex.getMessage}")
      }
    }

    Future.sequence(healthFutures) onComplete {
      case Failure(f) =>
        log.debug(f, "Failed to retrieve health of children objects")
        p success HealthComponent(health.name, ComponentState.CRITICAL, s"Failure to get health of child components. ${f.getMessage}")
      case Success(s) =>
        s foreach { cHealth => health.addComponent(cHealth) }
        p success health
    }

    p.future
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
    proxy = None
    distributor = None
    producer = None
  }
}

object Kafka {
  val ComponentName = "wookie-kafka"
}
