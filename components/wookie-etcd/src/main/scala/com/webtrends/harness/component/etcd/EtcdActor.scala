/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:23 PM
 */
package com.webtrends.harness.component.etcd

import akka.actor._
import akka.pattern._
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.StopComponent
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import net.nikore.etcd.EtcdClient
import net.nikore.etcd.EtcdJsonProtocol.EtcdResponse

import scala.concurrent._

object EtcdActor {
  def props = Props(classOf[EtcdActor])
}

class EtcdActor extends HActor {
  override def receive = super.receive orElse {
    case SetKey(key:String, value:String) =>
      val client = new EtcdClient("http://localhost:4001")
      client.setKey(key, value)

    case RemoveKey(key:String) =>
      val client = new EtcdClient("http://localhost:4001")
      client.deleteKey(key)

    case GetKey(key:String) =>
      val client = new EtcdClient("http://localhost:4001")
      pipe(client.getKey(key)) to sender
  }
}