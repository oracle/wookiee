/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.oracle.infy.wookiee.component.messages

/**
  * This is messages that components would use to easily communicate with components that
  * it doesn't know whether they are loaded in the system or not
  */
sealed class MetricMessage
case class StatusRequest(format: String = "json") extends MetricMessage
case class RemoveMetric() extends MetricMessage
case class AddTimerMetric(name: String) extends MetricMessage
case class AddGaugeMetric(name: String) extends MetricMessage
case class GetMetric(name: String) extends MetricMessage
