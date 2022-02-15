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

package com.oracle.infy.wookiee.http

import akka.actor.ActorSelection

import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import akka.pattern.ask
import com.oracle.infy.wookiee.app.HActor
import com.oracle.infy.wookiee.authentication.CIDRRules
import com.oracle.infy.wookiee.component.{ComponentHelper, ComponentRequest}
import com.oracle.infy.wookiee.component.messages.StatusRequest
import com.oracle.infy.wookiee.health.{
  ApplicationHealth,
  ComponentState,
  HealthComponent,
  HealthRequest,
  HealthResponseType
}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.oracle.infy.wookiee.HarnessConstants
import com.oracle.infy.wookiee.health.HealthResponseType.HealthResponseType
import com.oracle.infy.wookiee.service.ServiceManager.GetMetaDataByName
import com.oracle.infy.wookiee.service.messages.GetMetaData
import com.oracle.infy.wookiee.utils.Json
import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class Stop()
case class Start()

/**
  * This is a simple http server, which is primarily started for HealthChecks
  * And will have basic CIDR rules applied to it by default, but will be configurable.
  * This basic Http handler should not be extended to any functionality outside of
  * base functionality for the core server. For that use one of the Http Components
  * like Spray, Socko or Netty.
  *
  * @author Michael Cuthbert on 2/3/15.
  */
class SimpleHttpServer(port: Int = 8008) extends HActor with ComponentHelper {
  import context.dispatcher
  var httpServer: Option[HttpServer] = None
  val healthActor: ActorSelection = context.actorSelection(HarnessConstants.HealthFullName)
  val servicesActor: ActorSelection = context.actorSelection(HarnessConstants.ServicesFullName)

  val cidrRules: Option[CIDRRules] = if (config.hasPath("cidr-rules")) {
    Some(CIDRRules(config))
  } else {
    None
  }

  var portBound = false

  def respond(
      httpExchange: HttpExchange,
      response: String,
      responseStatus: Int = 200,
      contentType: String = "text/plain"
  ): Unit = {
    httpExchange.getResponseHeaders.set("Content-Type", contentType)
    httpExchange.sendResponseHeaders(responseStatus, response.length())
    if (response.nonEmpty) {
      try {
        httpExchange.getResponseBody.write(response.getBytes)
      } catch {
        case _: IOException => log.debug("Broken Pipe, client-side must have severed connection...")
      }
    }
    httpExchange.close()
  }

  def checkCidrRules(httpExchange: HttpExchange): Boolean = {
    cidrRules match {
      case Some(s) => s.checkCidrRules(httpExchange.getRemoteAddress.getAddress)
      case None    =>
        // no cidr rules, only allowing localhost to match
        val ip = httpExchange.getRemoteAddress.getAddress
        ip.isAnyLocalAddress || ip.isLoopbackAddress
    }
  }

  def handleHealthMessage(httpExchange: HttpExchange, hType: HealthResponseType): Unit = {
    (healthActor ? HealthRequest(hType)) onComplete {
      case Success(s) =>
        s match {
          case ah: ApplicationHealth =>
            respond(httpExchange, Json.build(ah).toString)
          case resp: String => respond(httpExchange, resp)
          case _            => respond(httpExchange, "Failed to retrieve health", 500)
        }
      case Failure(f) => respond(httpExchange, f.getMessage, 500)
    }
  }

  def handleMetricsMessage(httpExchange: HttpExchange): Unit = {
    componentRequest[StatusRequest, String]("wookiee-metrics", ComponentRequest(StatusRequest("string"))) onComplete {
      case Success(s) => respond(httpExchange, s.resp)
      case Failure(f) => respond(httpExchange, f.getMessage, 500)
    }
  }

  def handleServicesMessage(httpExchange: HttpExchange, path: Option[String]): Unit = {
    val msg = path match {
      case None    => GetMetaData(None)
      case Some(x) => GetMetaDataByName(x)
    }
    (servicesActor ? msg) onComplete {
      case Success(s) => respond(httpExchange, Json.build(s).toString, 200, "application/json")
      case Failure(f) => respond(httpExchange, f.getMessage, 500)
    }
  }

  def start(): Unit = {
    if (!isStarted) {
      try {
        val server = HttpServer.create(new InetSocketAddress(port), 0)
        httpServer = Some(server)
        val internalHandler = new HttpHandler() {
          override def handle(httpExchange: HttpExchange): Unit = {
            if (checkCidrRules(httpExchange)) {
              httpExchange.getRequestURI.getPath match {
                case "/healthcheck" | "/healthcheck/full" =>
                  val query = httpExchange.getRequestURI.getQuery
                  if (query != null && query.contains("type=lb")) {
                    handleHealthMessage(httpExchange, HealthResponseType.LB)
                  } else handleHealthMessage(httpExchange, HealthResponseType.FULL)
                case "/healthcheck/lb"     => handleHealthMessage(httpExchange, HealthResponseType.LB)
                case "/healthcheck/nagios" => handleHealthMessage(httpExchange, HealthResponseType.NAGIOS)
                case "/ping" =>
                  respond(
                    httpExchange,
                    "pong: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString)
                  )
                case "/metrics" => handleMetricsMessage(httpExchange)
                case x if x.startsWith("/services") =>
                  val p = x.split("/")
                  val path = if (p.length == 3) {
                    Some(p(2))
                  } else {
                    None
                  }
                  handleServicesMessage(httpExchange, path)
              }
            } else {
              // Could be a 403 Forbidden, but obscurity is better
              respond(httpExchange, "", 404)
            }
          }
        }

        server.createContext("/healthcheck/lb", internalHandler)
        server.createContext("/healthcheck", internalHandler)
        server.createContext("/healthcheck/full", internalHandler)
        server.createContext("/healthcheck/nagios", internalHandler)
        server.createContext("/ping", internalHandler)
        server.createContext("/metrics", internalHandler)
        server.createContext("/services", internalHandler)
        server.setExecutor(Executors.newCachedThreadPool())
        server.start()
        log.info(s"Internal Http started on port $port")
      } catch {
        case ioe: IOException =>
          log.warn(s"Internal server failed to start, HTTP port $port already bound")
          log.trace(ioe.getMessage, ioe)
          portBound = true
      }
    }
  }

  def isStarted: Boolean = {
    httpServer match {
      case Some(_) => true
      case None    => false
    }
  }

  override def preStart(): Unit = {
    if (!isStarted) {
      start()
    }
  }

  override def postStop(): Unit = {
    if (isStarted) {
      log.info("Shutting down internal http server")
      httpServer.get.stop(0)
    }
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
    Future {
      httpServer match {
        case Some(_) =>
          HealthComponent(self.path.toString, ComponentState.NORMAL, s"Internal HTTP Server started on port $port")
        case None if portBound =>
          HealthComponent(
            self.path.toString,
            ComponentState.NORMAL,
            s"Internal HTTP server not started, port [$port] already bound by http component"
          )
        case None => HealthComponent(self.path.toString, ComponentState.CRITICAL, "Internal HTTP Server not started")
      }
    }
  }
}
