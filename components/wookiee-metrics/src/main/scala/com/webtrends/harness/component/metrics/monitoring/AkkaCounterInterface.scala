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
//package com.webtrends.portfolio.harness.monitoring
//
//import org.eigengo.monitor.output.CounterInterface
//import com.webtrends.portfolio.metrics.{Timer, Gauge, Counter}
//import com.webtrends.portfolio.messages.metrics.{TimerObservation, GaugeObservation, CounterObservation}
//import com.webtrends.portfolio.metrics.MetricsBuilder
//import java.util.concurrent.TimeUnit
//
//class AkkaCounterInterface extends CounterInterface {
//  /**
//   * Increment the counter identified by {@code aspect} by one.
//   *
//   * @param aspect the aspect to increment
//   * @param tags optional tags
//   */
//  def incrementCounter(aspect: String, tags: String*): Unit = {
//    MetricBuilder(CounterObservation(Counter(buildName(aspect, tags)), 1)).inc
//  }
//
//  def incrementCounter(aspect: String, delta: Int, tags: String*): Unit = {
//    MetricBuilder(CounterObservation(Counter(buildName(aspect, tags)), delta)).inc(delta)
//  }
//
//  /**
//   * Decrement the counter identified by {@code aspect} by one.
//   *
//   * @param aspect the aspect to decrement
//   * @param tags optional tags
//   */
//  def decrementCounter(aspect: String, tags: String*): Unit = {
//    MetricBuilder(CounterObservation(Counter(buildName(aspect, tags)), -1)).dec
//    //Counter(buildName(aspect, tags)).incr(-1)
//  }
//
//  /**
//   * Records gauge {@code value} for the given {@code aspect}, with optional {@code tags}
//   *
//   * @param aspect the aspect to record the value for
//   * @param value the value
//   * @param tags optional tags
//   */
//  def recordGaugeValue(aspect: String, value: Int, tags: String*): Unit =  {
//    MetricBuilder(GaugeObservation(Gauge(buildName(aspect, tags)), value)).setValue(value)
//    //Gauge(buildName(aspect, tags)).update(value)
//  }
//
//  /**
//   * Records the execution time of the given {@code aspect}, with optional {@code tags}
//   *
//   * @param aspect the aspect to record the execution time for
//   * @param duration the execution time (most likely in ms)
//   * @param tags optional tags
//   */
//  def recordExecutionTime(aspect: String, duration: Int, tags: String*): Unit = {
//    MetricBuilder(TimerObservation(Timer(buildName(aspect, tags)), duration, TimeUnit.MILLISECONDS)).update(duration, TimeUnit.MILLISECONDS)
//    //Timer(buildName(aspect, tags)).record(duration)
//  }
//
//  private def buildName(aspect: String, tags: Seq[String]): String = {
//    def fixPath: String =  PluginPathString(UserPathString(tags.head))
//
//    aspect match {
//      case "akka.actor.count" =>
//        // This is the actor count
//        s"harness.actor-count"
//      case "akka.queue.size" =>
//        // This is the queue size
//        s"$fixPath.queue-size"
//      case _ => // These are all actor specific
//        s"$fixPath.${aspect.replace("akka.actor.", "").replace("$","").toLowerCase}"
//    }
//  }
//
//  object UserPathString{
//    def apply(str:String): String= {
//      (str match {
//        case s if s.startsWith("akka://server/user/") => s.stripPrefix("akka://server/user/")
//        case s if s.startsWith("akka://server/system/") => s.replace("akka://server/system/", "internal.")
//        case s => s.replace("akka://server/", "internal.")
//      }).replace('/', '.').toLowerCase
//    }
//  }
//
//  object PluginPathString{
//    def apply(str:String): String = str match {
//      case s if s.startsWith("system.plugins.") => s.stripPrefix("system.plugins.")
//      case s => s"harness.$s"
//    }
//  }
//}
