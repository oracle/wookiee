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
package com.webtrends.harness.component.cluster.communication

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import org.joda.time.DateTime
import org.specs2.mutable.SpecificationWithJUnit

class MessageSerializerSpec extends SpecificationWithJUnit {

  val system = ActorSystem.create("test", ConfigFactory.parseString(
    """
    """))

  val ext = SerializationExtension(system)
  val ser = new KryoSerializer(ext.system)
  sequential

  "The kryo serializer" should {

    "serialize case class messages wrapped in a Message object" in {
      val msg = TestCase("name", true, DateTime.now)
      val wrap = Message.createMessage("test", msg)
      val bin = ser.toBinary(wrap)
      bin.length must be greaterThan 0

      ser.fromBinary(bin) mustEqual wrap
      ser.fromBinary(bin, Some(classOf[Message])) mustEqual wrap
      ser.fromBinary(bin, classOf[Message]) mustEqual wrap
    }

    "serialize classes with enumerations wrapped in a Message object" in {

      val msg = HealthComponent("name", ComponentState.CRITICAL, "details go here", Some(TestCase("name", true, DateTime.now)))
      msg.addComponent(HealthComponent("name", ComponentState.CRITICAL, "child component"))

      val wrap = Message.createMessage("test", msg)
      val bin = ser.toBinary(wrap)
      bin.length must be greaterThan 0

      val res = ser.fromBinary(bin)
      ser.fromBinary(bin) mustEqual wrap
      ser.fromBinary(bin, Some(classOf[Message])) mustEqual wrap
      ser.fromBinary(bin, classOf[Message]) mustEqual wrap
    }
  }

  step {
    TestKit.shutdownActorSystem(system)
  }
}

case class TestCase(name: String, bool: Boolean, timestamp: DateTime)

