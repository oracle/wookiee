/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:23 PM
 */
package com.webtrends.harness.component.etcd

import akka.actor._
import akka.pattern.pipe
import com.webtrends.harness.app.HActor
import net.nikore.etcd.EtcdClient


object EtcdActor {
  def props(settings:EtcdSettings): Props = Props(classOf[EtcdActor], settings)
}

class EtcdActor(settings:EtcdSettings) extends HActor {
  import context.dispatcher

  var client:Option[EtcdClient] = None

  override def receive = super.receive orElse {
    case SetKey(key:String, value:String) =>
      client.foreach(_.setKey(key, value))

    case RemoveKey(key:String) =>
      client.foreach(_.deleteKey(key))

    case GetKey(key:String) =>
      client.foreach { x =>
        x.getKey(key) pipeTo sender
      }
  }

  override def preStart(): Unit = {
    client = Some(new EtcdClient(settings.EtcdEndpoint))
    super.preStart()
  }

  override def postStop(): Unit = {
    Some(client.foreach(_.shutdown))
    super.postStop()
  }
}