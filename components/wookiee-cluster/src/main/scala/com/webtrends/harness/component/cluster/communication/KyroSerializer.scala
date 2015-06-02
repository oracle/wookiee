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

/*
 * @author vonnagyi on 12/2/13 12:46 PM
 */
import akka.actor.ExtendedActorSystem
import com.esotericsoftware.kryo.Kryo
import com.twitter.chill.akka.{ActorRefSerializer, AkkaSerializer}
import com.twitter.chill.{IKryoRegistrar, ScalaKryoInstantiator, KryoInstantiator}
import de.javakaffee.kryoserializers.jodatime.JodaDateTimeSerializer
import org.joda.time.DateTime

class KryoSerializer(system: ExtendedActorSystem) extends AkkaSerializer(system) {
  override def kryoInstantiator: KryoInstantiator =
    (new ScalaKryoInstantiator)
      .withRegistrar(new ActorRefSerializer(system))
      .withRegistrar(new JodaSerializer)
}

class JodaSerializer extends JodaDateTimeSerializer with IKryoRegistrar {

  def apply(kryo: Kryo): Unit = {
    kryo.register(classOf[DateTime], new JodaDateTimeSerializer)
    kryo.setReferences(false)
  }
}
