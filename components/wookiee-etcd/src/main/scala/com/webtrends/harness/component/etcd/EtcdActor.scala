/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:23 PM
 */
package com.webtrends.harness.component.etcd

import akka.actor._
import akka.pattern.pipe
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.ComponentHelper
import net.nikore.etcd.EtcdClient
import net.nikore.etcd.EtcdJsonProtocol.{EtcdListResponse, EtcdResponse}

import scala.concurrent.Promise
import scala.util.{Failure, Success}


object EtcdActor {
  def props(settings:EtcdSettings): Props = Props(classOf[EtcdActor], settings)
}

class EtcdActor(settings:EtcdSettings) extends HActor with ComponentHelper{
  import context.dispatcher

  var client:Option[EtcdClient] = None

  override def receive = super.receive orElse {

    case SetKey(key:String, value:String) =>
      val p = Promise[Boolean]
      client.get .setKey(key, value) onComplete {
        case Success(s:EtcdResponse) =>
          p success (s.node.value.get == value)
        case Failure(f) =>
          p failure f
      }
      p.future pipeTo sender


    case RemoveKey(key:String) =>
      val p = Promise[Boolean]
      client.get.deleteKey(key) onComplete {
        case Success(s:EtcdResponse) =>
          p success !s.node.value.isDefined
        case Failure(f) =>
          p failure f
      }
      p.future pipeTo sender

    case GetKey(key:String) =>
      val p = Promise[String]
      client.get.getKey(key) onComplete {
        case Success(s:EtcdResponse) =>
          p success s.node.value.get
        case Failure(f) =>
          p failure f
      }
      p.future pipeTo sender

    case ListDir(dir:String, recursive:Boolean) =>
      val p = Promise[String]
      client.get.listDir(dir, recursive) onComplete {
        case Success(s:EtcdListResponse) =>
          p success s.node.toJson.compactPrint
        case Failure(f) =>
          p failure f
      }
      p.future pipeTo sender

  }

  override def preStart(): Unit = {
    client = Some(new EtcdClient(settings.EtcdEndpoint))
    super.preStart()
  }

  override def postStop(): Unit = {
    client.get.shutdown()
    super.postStop()
  }
}