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
package com.webtrends.harness.component.cluster

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.MemberStatus
import net.liftweb.json.JString
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.json.ext.JodaTimeSerializers

trait ClusterStateSerializer {

  implicit val formats = net.liftweb.json.DefaultFormats + new AddressSerializer + new MemberStatusSerializer ++ JodaTimeSerializers.all

  def serializeState(state: CurrentClusterState): JValue = {
    implicit val order: Ordering[Address] = Ordering.by(_.toString)

    "cluster" ->
      ("leader" -> (state.leader match {
        case Some(lead) => lead.toString
        case None => "N/A"
      })) ~
        ("peer-members" ->
          state.seenBy.toSeq.sorted.map {
            m => m.toString
          }
          ) ~
        ("active-members" ->
          state.members.toSeq.sortBy(_.address).map {
            m =>
              ("address" -> m.address.toString) ~
                ("status" -> m.status.toString) ~
                ("roles" -> m.roles)
          }
          ) ~
        ("unreachable-members" ->
          state.unreachable.toSeq.sortBy(_.address).map {
            m =>
              ("address" -> m.address.toString) ~
                ("status" -> m.status.toString) ~
                ("roles" -> m.roles)
          }
          ) ~
        ("roles" ->
          state.allRoles.toSeq.sorted.map {
            r =>
              ("role" -> r) ~
                ("leader" -> (state.roleLeader(r) match {
                  case Some(lead) => lead.toString
                  case None => "N/A"
                })) ~
                ("members" -> (state.members ++ state.unreachable).filter(_.roles.contains(r)).toSeq.sorted.map {
                  m => m.address.toString
                })
          })
  }

  class AddressSerializer extends Serializer[Address] {
    private val Class = classOf[Address]

    def deserialize(implicit format: Formats) = {
      case (TypeInfo(Class, _), json) => json match {
        case JString(s) => Address("", "", "", 8080)
        case x => throw new MappingException("Can't convert " + x + " to MemberStatus")
      }
    }

    def serialize(implicit format: Formats) = {
      case s: Address => JString(s.toString)
    }
  }

  class MemberStatusSerializer extends Serializer[MemberStatus] {

    import MemberStatus._

    private val Class = classOf[MemberStatus]

    def deserialize(implicit format: Formats) = {
      case (TypeInfo(Class, _), json) => json match {
        case JString(s) => Joining
        case x => throw new MappingException("Can't convert " + x + " to MemberStatus")
      }
    }

    def serialize(implicit format: Formats) = {
      case s: MemberStatus => JString(s.toString)
    }
  }

}
