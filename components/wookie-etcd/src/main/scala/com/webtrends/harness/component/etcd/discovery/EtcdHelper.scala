package com.webtrends.harness.component.etcd.discovery

import akka.actor.{ActorRef, Actor}
import akka.pattern._
import akka.util.Timeout
import com.webtrends.harness.component.ComponentException
import com.webtrends.harness.component.etcd.{SetKey, RemoveKey, GetKey, Etcd}
import scala.concurrent.duration._
import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success}

trait EtcdHelper {
  this: Actor =>
  import context.dispatcher
  var etcdManager:Option[ActorRef] = None
  var etcdManagerInitialized:Boolean = false

  def initEtcdHelper : Future[ActorRef] = {
    val p = Promise[ActorRef]()

    def awaitEtcdManager(timeOut: Deadline) {
      if (timeOut.isOverdue() && !etcdManagerInitialized) {
        etcdManagerInitialized = true
        p failure ComponentException("Etcd Component", "Failed to get etcd manager")
      }
      context.actorSelection(Etcd.EtcdName).resolveOne()(1 second) onComplete {
        case Success(s) =>
          etcdManager = Some(s)
          etcdManagerInitialized = true
          p success s
        case Failure(f) => awaitEtcdManager(timeOut)
      }
    }

   etcdManager match {
      case Some(cm) => p success cm
      case None =>
        if (!etcdManagerInitialized) {
          val deadline = 5 seconds fromNow
          awaitEtcdManager(deadline)
        } else {
          p failure ComponentException("Etcd Component", "Etcd manager did not initialize")
        }
    }
    p.future

  }

  def locate(path:String): Future[AnyRef] = {
    val f = initEtcdHelper
    f onSuccess {
      case actor =>
        actor ! GetKey(path)
    }
    f
  }

  def delete(path:String): Future[AnyRef] = {

    val f = initEtcdHelper
    f onSuccess {
      case actor =>
        actor ! RemoveKey(path)
    }
    f
  }

  def publish(path:String, value:AnyRef): Future[AnyRef] = {
    val f = initEtcdHelper
    f onSuccess  {
      case actor =>
        actor ! SetKey(path, value)
    }
    f
  }

}
