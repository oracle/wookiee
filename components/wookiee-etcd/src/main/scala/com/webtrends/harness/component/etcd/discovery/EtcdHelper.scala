package com.webtrends.harness.component.etcd.discovery

import akka.actor.{Actor, ActorRef}
import akka.pattern._
import akka.util.Timeout
import com.webtrends.harness.component.ComponentException
import com.webtrends.harness.component.etcd._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

trait EtcdHelper {
  this: Actor =>

  import context.dispatcher

  var etcdManager:Option[ActorRef] = None
  var etcdManagerInitialized:Boolean = false

  implicit val timeout = Timeout(5 seconds)

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

  def list(path:String, recursive:Boolean = false): Future[String] = {
    var f:Option[Future[String]] = None
    initEtcdHelper onComplete {
      case Success(actor) =>
        val t:Future[String] = ask(actor, ListDir(path, recursive)).mapTo[String]
        f = Option(t)
      case Failure(e) =>
        f = Option(Future.failed(e))
    }
    f.get
  }

  def locate(path:String): Future[String] = {
    var f:Option[Future[String]] = None
    initEtcdHelper onComplete {
      case Success(actor) =>
        val t:Future[String] = ask(actor, GetKey(path)).mapTo[String]
        f = Option(t)
      case Failure(e) =>
        f = Option(Future.failed(e))
    }
    f.get
  }

  def delete(path:String): Future[Boolean] = {
    var f:Option[Future[Boolean]] = None
    initEtcdHelper onComplete {
      case Success(actor) =>
        val t:Future[Boolean] = ask(actor, RemoveKey(path)).mapTo[Boolean]
        f = Option(t)
      case Failure(e) =>
        f = Option(Future.failed(e))
    }
    f.get
  }

  def publish(path:String, value:String): Future[Boolean] = {
    var f:Option[Future[Boolean]] = None
    initEtcdHelper onComplete {
      case Success(actor) =>
        val t:Future[Boolean] = ask(actor, SetKey(path, value)).mapTo[Boolean]
        f = Option(t)
      case Failure(e) =>
        f = Option(Future.failed(e))
    }
    f.get
  }

}
