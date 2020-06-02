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
package com.webtrends.harness.health

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.app.HActor
import com.webtrends.harness.service.messages.CheckHealth
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class HealthCheckActorSpec extends WordSpecLike with MustMatchers with BeforeAndAfterAll {

  implicit val dur: FiniteDuration = FiniteDuration(15, TimeUnit.SECONDS)

  implicit val sys: ActorSystem = ActorSystem("system", ConfigFactory.load())
  implicit val ec: ExecutionContextExecutor =  sys.dispatcher

  override protected def beforeAll(): Unit = {
    sys.actorOf(Props(new Actor {
      override def receive: Receive = {
        case CheckHealth => sender() ! Seq(HealthComponent("test", ComponentState.NORMAL, "test"))
      }
    }))
  }

  override protected def afterAll(): Unit = {
    sys.terminate().onComplete(_ => {})
  }

  // TODO: Refactor the following tests once we get a better testing library
  "healthChecksDiffer" should {
    val baseHealth = ApplicationHealth("a", "", DateTime.now, ComponentState.NORMAL, "", Seq())
    val baseComponentA = HealthComponent("subA", ComponentState.NORMAL, "")
    val baseComponentB = HealthComponent("subB", ComponentState.NORMAL, "")

    "return true when applicationState goes from one state to another" in {
      val changedStates = Seq(
        (baseHealth, baseHealth.copy(state = ComponentState.CRITICAL)),
        (baseHealth, baseHealth.copy(state = ComponentState.DEGRADED)),
        (baseHealth.copy(state = ComponentState.CRITICAL), baseHealth),
        (baseHealth.copy(state = ComponentState.CRITICAL), baseHealth.copy(state = ComponentState.DEGRADED)),
        (baseHealth.copy(state = ComponentState.DEGRADED), baseHealth),
        (baseHealth.copy(state = ComponentState.DEGRADED), baseHealth.copy(state = ComponentState.CRITICAL))
      )

      (for ((previousHealth, newHealth) <- changedStates) yield {
        HealthCheckActor.healthChecksDiffer(previousHealth, newHealth)
      }).forall(x => x)
    }

    "return false when application state doesn't change" in {
      val unchangedStates = Seq(
        (baseHealth, baseHealth.copy(state = ComponentState.NORMAL)),
        (baseHealth.copy(state = ComponentState.DEGRADED), baseHealth.copy(state = ComponentState.DEGRADED)),
        (baseHealth.copy(state = ComponentState.CRITICAL), baseHealth.copy(state = ComponentState.CRITICAL))
      )

      (for ((previousHealth, newHealth) <- unchangedStates) yield {
        HealthCheckActor.healthChecksDiffer(previousHealth, newHealth)
      }).forall(x => !x)
    }

    "return true when sub component state changes" in {
      val changedComplexStates = Seq(
        (baseHealth.copy(components = Seq(baseComponentA, baseComponentB)),
          baseHealth.copy(components = Seq(baseComponentA.copy(state = ComponentState.CRITICAL), baseComponentB))),
        (baseHealth.copy(components = Seq(baseComponentA, baseComponentB)),
          baseHealth.copy(components = Seq(baseComponentA.copy(state = ComponentState.CRITICAL), baseComponentB.copy(state = ComponentState.CRITICAL)))),
        (baseHealth.copy(components = Seq(baseComponentA, baseComponentB)),
          baseHealth.copy(components = Seq(baseComponentA, baseComponentB.copy(state = ComponentState.CRITICAL)))),
        (baseHealth.copy(components = Seq(baseComponentA.copy(components = List(baseComponentB)))),
          baseHealth.copy(components = Seq(baseComponentA.copy(components = List(baseComponentB.copy(state = ComponentState.DEGRADED))))))
      )

      (for ((previousHealth, newHealth) <- changedComplexStates) yield {
        HealthCheckActor.healthChecksDiffer(previousHealth, newHealth)
      }).forall(x => x)
    }

    "return false when sub component state remains same" in {
      val unchangedComplexStates = Seq(
        (baseHealth.copy(components = Seq(baseComponentA, baseComponentB.copy(state = ComponentState.CRITICAL))),
          baseHealth.copy(components = Seq(baseComponentA, baseComponentB.copy(state = ComponentState.CRITICAL)))),
        (baseHealth.copy(components = Seq(baseComponentA.copy(components = List(baseComponentA.copy(components = List(baseComponentB)))))),
          baseHealth.copy(components = Seq(baseComponentA.copy(components = List(baseComponentA.copy(components = List(baseComponentB)))))))
      )

      (for ((previousHealth, newHealth) <- unchangedComplexStates) yield {
        HealthCheckActor.healthChecksDiffer(previousHealth, newHealth)
      }).forall(x => !x)
    }
  }

  "collectHealthStates" should {
    val baseHealth = ApplicationHealth("a", "", DateTime.now, ComponentState.NORMAL, "", Seq())
    val baseComponent = HealthComponent("subA", ComponentState.CRITICAL, "")

    "map out ApplicationHealth objects" in {
      val baseMap = mutable.Map(Seq(baseHealth.applicationName) -> ComponentState.NORMAL)

      val baseWithSubComponent = baseHealth.copy(components = Seq(baseComponent))
      val baseWithSubComponentMap = mutable.Map(Seq(baseHealth.applicationName) -> ComponentState.NORMAL,
        Seq(baseHealth.applicationName, baseComponent.name) -> ComponentState.CRITICAL)

      val baseWithMultipleSubLevels = baseHealth.copy(components = Seq(baseComponent,
        baseComponent.copy(name = "b", state = ComponentState.DEGRADED, components = List(baseComponent))))
      val baseWithMultipleSubLevelsMap = mutable.Map(
        Seq(baseHealth.applicationName) -> ComponentState.NORMAL,
        Seq(baseHealth.applicationName, baseComponent.name) -> ComponentState.CRITICAL,
        Seq(baseHealth.applicationName, "b") -> ComponentState.DEGRADED,
        Seq(baseHealth.applicationName, "b", baseComponent.name) -> ComponentState.CRITICAL
      )

      val testPairs = Seq((baseHealth, baseMap), (baseWithSubComponent, baseWithSubComponentMap),
        (baseWithMultipleSubLevels,baseWithMultipleSubLevelsMap))

      (for ((input, expected) <- testPairs) yield (HealthCheckActor.collectHealthStates(input), expected))
        .forall(x => x._1 == x._2)
    }
  }

  class TopActor() extends HActor {
    override implicit val checkTimeout: Timeout = Timeout(2.seconds)
    val lActor: ActorRef = context.actorOf(Props(new LowerActor()), "lower")
  }

  class LowerActor() extends Actor { // Not a health actor, so won't respond to CheckHealth
    override def receive: Receive = {
      case _ =>
    }: Receive
  }
}
