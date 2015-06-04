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
package com.webtrends.harness.component.spray

import java.net.InetAddress
import akka.actor._
import akka.io.IO
import akka.pattern._
import akka.routing.Broadcast
import akka.util.Timeout
import com.webtrends.harness.app.HActor
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.utils.AkkaUtil
import spray.can.Http
import spray.can.server.{UHttp, Stats, ServerSettings}
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise, Future}
import scala.util._

object CoreSprayServer {
  def props(port:Int, settings:Option[ServerSettings]=None): Props = Props(classOf[CoreSprayServer], port, settings)

  val ParamChildWorker = "spray-base"
}

@SerialVersionUID(1L) case class BindServer()
@SerialVersionUID(1L) case class ShutdownServer()
@SerialVersionUID(1L) case class GetMetrics()

class CoreSprayServer(port:Int, settings:Option[ServerSettings]=None) extends HActor {
  import context.system
  import context.dispatcher

  implicit val timeout = Timeout(2 seconds)

  override def receive: Receive = binding

  def binding: Receive = {
    case BindServer => bindHttpServer
  }

  def running: Receive = health orElse {
    case HttpStartProcessing =>
      log.debug("Http processing starting.")
      context.child(CoreSprayServer.ParamChildWorker).get ! Broadcast(HttpStartProcessing)
    case HttpReloadRoutes =>
      log.debug("Http reloading routes.")
      context.child(CoreSprayServer.ParamChildWorker).get ! Broadcast(HttpReloadRoutes)
    case ShutdownServer => shutdownHttpServer
  }

  var metricsJob: Option[Cancellable] = None

  def httpServer = IO(UHttp)

  /**
   * Bind to the http service
   */
  protected def bindHttpServer: Unit = {
    // a running HttpServer can be bound, unbound and rebound
    // initially to need to tell it where to bind to
    log.info(s"Trying to bind to port ${port}")

    // Create and start the spray-can HttpServer, telling it that we want
    // requests to be handled by the root service actor
    if (context.child(CoreSprayServer.ParamChildWorker).isEmpty) {
      (httpServer ? Http.Bind(listener = AkkaUtil.initActorFromConfig(Props[CoreSprayWorker], CoreSprayServer.ParamChildWorker),
        interface = "0.0.0.0", port = port, settings = settings))
        .onComplete {
          case Failure(e) =>
            log.error(s"Error trying to bind to the http port ${port}", e)
            throw e
          case Success(s) =>
            context.become(running)
            context.parent ! HttpRunning
      }
    }
  }

  /**
   * Unbind from the http service
   */
  protected def shutdownHttpServer: Unit = {

    metricsJob match {
      case Some(job) =>
        log.info("Cancelling the http server metrics job")
        job.cancel
      case None =>
      // Do nothing
    }

    log.info(s"Trying to shutdown the http server ${InetAddress.getLocalHost.getHostName} on port ${port}")
    val f = httpServer ? Http.CloseAll
    Try(Await.result(f, 1 second)).recover({
      case e: Throwable =>
        log.warning("we were unable to properly shutdown the HttpServer: {}", e.getMessage)
    })

    log.info("Http server shutdown complete")
    context.become(binding)
  }

  /**
   * This is the health of the current object, by default will be NORMAL
   * In general this should be overridden to define the health of the current object
   * For objects that simply manage other objects you shouldn't need to do anything
   * else, as the health of the children components would be handled by their own
   * CheckHealth function
   *
   * @return
   */
  override protected def getHealth: Future[HealthComponent] = {
    val future = system.actorSelection(httpServer.path.toString.concat("/listener-0")) ? Http.GetStats
    val p = Promise[HealthComponent]()

    future.onComplete {
      case f: Failure[_] =>
        p complete Success(HealthComponent(
          "http-server",
          ComponentState.CRITICAL,
          "The internal http server is not running: " + f.exception.getMessage,
          None)
        )
      case s: Success[_] =>
        p complete Success(HealthComponent(
          "http-server",
          ComponentState.NORMAL,
          s"The internal http server is running on port ${port}",
          Some(s.value.asInstanceOf[Stats]))
        )
    }

    p.future
  }
}
