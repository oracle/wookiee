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

package com.webtrends.harness.component.socko.client

import java.io.{BufferedReader, InputStreamReader, DataOutputStream}
import java.net.{URL, HttpURLConnection}

import akka.pattern.ask
import akka.pattern.pipe
import akka.actor.Props
import akka.util.Timeout
import com.webtrends.harness.app.HActor

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.Duration
import scala.util.{Success, Failure}
import scala.collection.JavaConverters._

/**
 * @author Michael Cuthbert on 1/30/15.
 */
object CoreSockoClient {
  def props: Props = Props[CoreSockoClient]
}

class CoreSockoClient extends HActor {
  import context.dispatcher

  /**
   * Will route messages to correct behavior, mainly will go to service
   */
  override def receive = super.receive orElse {
    case HttpGet(config, path, headers) => service(config, HttpConstants.GET, path, None, headers)
    case HttpPost(config, path, body, headers) => service(config, HttpConstants.POST, path, Some(body), headers)
    case HttpPut(config, path, body, headers) => service(config, HttpConstants.PUT, path, Some(body), headers)
    case HttpDelete(config, path, headers) => service(config, HttpConstants.DELETE, path, None, headers)
    case HttpOptions(config, path, body, headers) => service(config, HttpConstants.OPTIONS, path, Some(body), headers)
    case HttpPatch(config, path, body, headers) => service(config, HttpConstants.PATCH, path, Some(body), headers)
    case HttpPing(config, timeout, path) => pipe(ping(config, timeout, path)) to sender
  }

  private def getHeaderFields(conn:HttpURLConnection) : Map[String, String] = {
    conn.getHeaderFields.asScala map {
      case (x, y) => x -> y.asScala.mkString(",")
    } toMap
  }

  def service[T:Manifest](config:HttpConfig, method:String, path:String, body:Option[T], headers:Map[String, String]) = {
    val urlConnection = new URL(config.fullPath(path)).openConnection().asInstanceOf[HttpURLConnection]
    urlConnection.setRequestMethod(method)
    headers foreach {
      case (name, value) => urlConnection.setRequestProperty(name, value)
    }
    method match {
      case HttpConstants.POST | HttpConstants.PUT =>
        body match {
          case Some(v) =>
            urlConnection.setDoOutput(true)
            val wr = new DataOutputStream(urlConnection.getOutputStream)
            // TODO marshall this
            wr.writeBytes(v.toString)
            wr.flush()
            wr.close()
          case None => // ignore
        }
      case _ => // don't have to do anything special with the other ones
    }

    try {
      urlConnection.connect()
      val responseCode = urlConnection.getResponseCode

      val response = try {
        val in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream))
        var inputLine: String = null
        val response = new StringBuffer()
        while ((inputLine = in.readLine()) != null) {
          response.append(inputLine)
        }
        in.close()
        response.toString.getBytes
      } catch {
        case t: Throwable =>
          log.debug(t, t.getMessage)
          new Array[Byte](0)
      }
      sender ! HttpResp(response, getHeaderFields(urlConnection), responseCode)
    } finally {
      urlConnection.disconnect()
    }
  }

  def ping(config:HttpConfig, timeoutValue:Int=1, pingPath:String="ping") : Future[ConnectionStatus] = {
    implicit val timeout = Timeout(Duration(timeoutValue, "seconds"))
    val p = Promise[ConnectionStatus]()
    try {
      val f = (self ? HttpGet(config, pingPath))(timeout)
      f onComplete {
        case Failure(f) =>
          p success ConnectionStatus(false, "Could not connect to " + config.fullPath("ping") + " responded with exception " + f.getMessage)
        case Success(s) =>
          s.asInstanceOf[HttpResp].statusCode match {
            case 200 => p success ConnectionStatus(true, "Connected successfully to " + config.fullPath(pingPath))
            case _ => p success ConnectionStatus(false, "Could not connect to " + config.fullPath(pingPath))
          }
      }
    } catch {
      case e:Throwable =>
        log.debug("Could not connect to " + config.fullPath(pingPath) + " responded with error " + e.getMessage)
        p success ConnectionStatus(false, "Could not connect to " + config.fullPath(pingPath) + " responded with error " + e.getMessage)
    }
    p.future
  }
}
