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
package com.webtrends.harness.service.test

import java.net.{URI, URLEncoder}

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.app.HarnessActor.PrepareForShutdown
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.logging.{ActorLoggingAdapter, LoggingAdapter}
import com.webtrends.harness.service.messages.{CheckHealth, GetMetaData, GetMetaDetails, Ready}
import com.webtrends.harness.service.meta.{ServiceMetaData, ServiceMetaDetails}
import com.webtrends.harness.service.test.TestSystemActor.RegisterShutdownListener
import org.joda.time.DateTime
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object TestSystemActor {

  /**
   * Create an instance of TestSystemActor
   * @param services the initial services to load. This can be empty and additional services loaded later.
   * @param system the ActorSystem to load the actor into
   * @return
   */
  def apply(services: Seq[Class[_ <: Actor]])(implicit system: ActorSystem): ActorRef = {
    val httpPort = Try(system.settings.config.getInt("internal-http.port")).getOrElse(8080)
    system.actorOf(Props(classOf[TestSystemActor], services, httpPort), "system")
  }

  /**
   * This message is used to get an instance of the ActorRef for the given service class. The return will be
   * an Option[ActorRef] for the service
   * @param service the class for the service to receive
   */
  case class GetService(service: Class[_ <: Actor])

  case class LoadService(service: Class[_ <: Actor], servicePath: Option[URI])

  case class Shutdown()

  case class RegisterShutdownListener(ref: ActorRef)
}

private[test] class TestSystemActor(serviceSeq: Seq[Class[_ <: Actor]], val httpPort: Int)
  extends Actor {

  implicit val sys: ActorSystem = context.system
  implicit val timeout: Timeout = Timeout(1.second)

  import context.dispatcher

  var services: Map[String, ActorRef] = Map[String, ActorRef]()

  override def preStart: Unit = {
    loadServices
  }

  private def loadServices: ActorRef = {
    context.actorOf(Props(new Actor with LoggingAdapter {
      serviceSeq foreach {
        loadService(self, _, None)
      }

      var serviceMeta: Map[ActorPath, ServiceMetaData] = Map[ActorPath, ServiceMetaData]()

      override def receive: PartialFunction[Any, Unit] = {
        case m: GetMetaData =>
          sender() ! serviceMeta.get(m.service.get)
        case TestSystemActor.LoadService(clazz, path) =>
          loadService(sender(), clazz, path)
        case TestSystemActor.GetService(clazz) =>
          sender() ! services.get(URLEncoder.encode(clazz.getSimpleName.toLowerCase, "utf-8"))
        case CheckHealth =>
          val xSender = sender()
          val future = Future.traverse(context.children)(plug => (plug ? CheckHealth)(2.second)).mapTo[Seq[HealthComponent]]

          future.onComplete({
            case Failure(f) =>
              xSender ! HealthComponent("service-manager", ComponentState.CRITICAL, f.getMessage)
            case Success(answers) =>
              // Create our health and include the services
              val comp = HealthComponent("service-manager", ComponentState.NORMAL, "All's good")
              answers foreach {
                comp.addComponent
              }
              xSender ! comp
          })
      }

      private def loadService(ref: ActorRef, clazz: Class[_ <: Actor], servicePath: Option[URI]): Unit = {
        try {
          val name = URLEncoder.encode(clazz.getSimpleName.toLowerCase, "utf-8")
          val service = context.actorOf(Props(clazz), name)
          services += (name -> service)

          val path = if (servicePath.isDefined) servicePath.get.getPath else "not provided"
          val jar = clazz.getProtectionDomain.getCodeSource.getLocation.toURI.toString

          (service ? GetMetaDetails).mapTo[ServiceMetaDetails] onComplete {
            case Success(m) =>
              val meta = ServiceMetaData(name, "n/a", DateTime.now, path, service.path.toString, jar, m.supportsHttp, Nil)
              serviceMeta += (service.path -> meta)

              service ! Ready(meta)
              ref ! Some(service)
            case Failure(t) =>
              log.error("An error occurred trying to load the service", t)
              ref ! None
          }
        } catch {
          case ex: Exception =>
            log.error("An error occurred trying to load the service", ex)
            ref ! None
        }
      }
    }))
  }

  def receive: Receive = {
    case lp: TestSystemActor.LoadService =>
      (context.actorSelection("services") ! lp)(sender())
    case gm: TestSystemActor.GetService =>
      (context.actorSelection("services") ! gm)(sender())
    case TestSystemActor.Shutdown =>
    case _ => // do nothing
  }
}


trait ShutdownListener {
  this: Actor with ActorLoggingAdapter =>

  var shutdownListener: Option[ActorRef] = None

  def shutdownReceive: Receive = {
    case PrepareForShutdown =>
      log.info("Preparing for shutdown")
      shutdownListener.foreach(_ ! "GotShutdown")
    case RegisterShutdownListener(ref) =>
      shutdownListener = Some(ref)
  }

}