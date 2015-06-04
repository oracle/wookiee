/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:23 PM
 */
package com.webtrends.harness.component.etcd

import akka.actor.ActorRef
import com.webtrends.harness.component.Component

trait Etcd { this: Component =>

  implicit val etcdSettings:EtcdSettings

  var EtcdRef:Option[ActorRef] = None

  def startEtcdComponent : ActorRef = {
    EtcdRef = Some(context.actorOf(EtcdActor.props(etcdSettings:EtcdSettings), Etcd.EtcdName))
    EtcdRef.get
  }

  def stopEtcd = {
    //TODO execute any special logic here to shut down the component
  }
}

object Etcd {
  val EtcdName = "Etcd"
}