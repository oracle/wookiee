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
package com.webtrends.harness.component.socko

import akka.actor._
import akka.routing.Broadcast
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.ComponentHelper
import com.webtrends.harness.health._
import com.webtrends.harness.utils.AkkaUtil
import org.mashupbots.socko.routes._
import org.mashupbots.socko.webserver.WebServer
import scala.concurrent.{Promise, Future}
import scala.util.Try

object SockoHttpServer {
  def props = Props(classOf[SockoHttpServer])

  val ParamWorker = "socko-base"
}

@SerialVersionUID(1L) case class BindServer()
@SerialVersionUID(1L) case class HttpStartProcessing()
@SerialVersionUID(1L) case class ShutdownServer()

class SockoHttpServer extends HActor with ComponentHelper {
  val config = SockoServerSettings.get(context.system)
  val routees = Try { context.system.settings.config.getInt(SockoManager.KeyNumHandlerRoutees) } getOrElse 5
  val router = AkkaUtil.initActorFromConfig(Props[SockoHttpWorker], SockoHttpServer.ParamWorker, routees)
  var webServer:Option[WebServer] = None

  override def receive:Receive = binding

  def binding:Receive = {
    case BindServer => bindHttpServer
    case HttpStartProcessing => context.become(running)
  }

  def running:Receive = health orElse {
    case HttpStartProcessing =>
      log.debug("Http processing starting.")
      context.child(SockoHttpServer.ParamWorker).get ! Broadcast(HttpStartProcessing)
    case ShutdownServer => shutdownHttpServer
  }

  /**
   * Base routes for the harness
   *
   */
  val routes = Routes({
    case HttpRequest(request) => router ! SockoWorkerMessage(request)
  })

  protected def bindHttpServer : Unit = {
    log.info(s"Trying to bind to port ${config.port}")

    webServer = Some(new WebServer(config, context.system, {server => new SockoRequestHandler(server, routes)}))
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run: Unit = {
        shutdownHttpServer
      }
    })
    Try({
      webServer.get.start()
      context.become(running)
      context.parent ! HttpRunning
      log.info(s"Bound to port ${config.port}")
    }).recover({
      case e:Throwable =>
        log.error(s"Error trying to bind to the http port ${config.port}", e)
        throw e
    })
  }

  /**
   * Unbind from the http service
   */
  protected def shutdownHttpServer: Unit = {
    log.info(s"Trying to shutdown the http server ${config.hostname} on port ${config.port}")
    webServer match {
      case Some(ws) =>
        Try(ws.stop()).recover({
          case e: Throwable =>
            log.warning("we were unable to properly shutdown the HttpServer: {}", e.getMessage)
        })
      case None => //ignore
    }
    log.info("Http server shutdown complete")
    if (context != null) {
      context.become(binding)
    }
  }

  // This should probably be overriden to get some custom information about the health of this actor
  override protected def getHealth: Future[HealthComponent] = {
    val p = Promise[HealthComponent]
    webServer match {
      case Some(s) => p success HealthComponent("socko", ComponentState.NORMAL, s"Web server up and running on port ${config.port}")
      case None => p success HealthComponent("socko", ComponentState.CRITICAL, "Web server not started")
    }
    p.future
  }
}