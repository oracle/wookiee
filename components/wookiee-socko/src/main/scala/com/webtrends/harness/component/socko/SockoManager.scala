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

import java.io.File

import akka.actor.Props
import com.webtrends.harness.app.HarnessActor.SystemReady
import com.webtrends.harness.component.socko.client.SockoClient
import com.webtrends.harness.component.socko.route.{SockoRouteHandler, SockoRouteManager}
import com.webtrends.harness.component.{Component, ComponentStarted}
import com.webtrends.harness.utils.{AkkaUtil, ConfigUtil}
import org.mashupbots.socko.events.{HttpRequestEvent, HttpResponseStatus}
import org.mashupbots.socko.handlers.{StaticContentHandler, StaticContentHandlerConfig, StaticFileRequest, StaticResourceRequest}

import scala.collection.JavaConverters._

case class HttpRunning()
case class AddSockoHandler(name:String, handler:SockoRouteHandler, method: String)
case class SockoContentRequest(event:HttpRequestEvent)

class SockoManager(name:String) extends Component(name) with Socko with SockoClient {

  val staticContentHandlerConfig =
    if (config.hasPath(SockoManager.KeyRootPaths)) {
      val rootPaths = config.getStringList(SockoManager.KeyRootPaths).asScala
      if (rootPaths.length > 0) {
        Some(StaticContentHandlerConfig(
          rootFilePaths = rootPaths,
          serverCacheMaxFileSize = ConfigUtil.getDefaultValue(SockoManager.KeyServerCacheMaxFileSize, config.getInt, 0),
          serverCacheTimeoutSeconds = ConfigUtil.getDefaultValue(SockoManager.KeyServerCacheTimeoutSeconds, config.getInt, 0),
          browserCacheTimeoutSeconds = ConfigUtil.getDefaultValue(SockoManager.KeyBrowserCacheTimeoutSeconds, config.getInt, 0)
        ))
      } else {
        None
      }
    } else {
      None
    }

  /**
   * We add super.receive because if you override the receive message from the component
   * and then do not include super.receive it will not handle messages from the
   * ComponentManager correctly and basically not start up properly
   *
   * @return
   */
  override def receive = super.receive orElse {
    case HttpRunning => context.parent ! ComponentStarted(self.path.name)
    case AddSockoHandler(cName, handler, method) => SockoRouteManager.addHandler(cName, handler, method)
    case SockoContentRequest(event) => handleStaticContentRequest(event)
    case SystemReady =>
      sockoServer match {
        case Some(s) => s ! HttpStartProcessing
        case None => // ignore
      }
  }

  /**
   * Start function will start any child actors that will be managed by the ComponentManager
   * @return
   */
  override def start = {
    // start the static file handler if the rootpaths have been set
    staticContentHandlerConfig match {
      case Some(c) => AkkaUtil.initActorFromConfig(Props(classOf[StaticContentHandler], c), SockoManager.StaticFileHandlerName)
      case None => log.debug("Static Content Handler not enabled.")
    }
    startSocko
    //startSockoClient
  }

  /**
   * Stop will execute any cleanup work to be done for the child actors
   * if not necessary this can be deleted
   * @return
   */
  override def stop = {
    super.stop
    stopSocko
  }

  def handleStaticContentRequest(event:HttpRequestEvent) = {
    context.child(SockoManager.StaticFileHandlerName) match {
      case Some(s) =>
        ConfigUtil.getDefaultValue(SockoManager.KeyFileType, config.getString, "file") match {
          case "jar" =>
            s ! StaticResourceRequest(event, staticContentHandlerConfig.get.rootFilePaths(0))
          case _ =>
            // check if file exists first otherwise we get a FileNotFoundException
            val f = new File(staticContentHandlerConfig.get.rootFilePaths(0) + "/" + event.endPoint.path)
            if (f.exists()) {
              s ! StaticFileRequest(event, f)
            }
        }

      case None => event.response.write(HttpResponseStatus.NOT_FOUND)
    }
  }
}

object SockoManager {
  val ComponentName = "wookiee-socko"

  val KeyNumHandlerRoutees = "wookiee-socko.num-handler-routees"
  val StaticFileHandlerName = "static-file-handler"
  val StaticContentRoot = "wookiee-socko.static-content"
  val KeyRootPaths = s"$StaticContentRoot.rootPaths"
  val KeyServerCacheMaxFileSize = s"$StaticContentRoot.serverCacheMaxFileSize"
  val KeyServerCacheTimeoutSeconds = s"$StaticContentRoot.serverCacheTimeoutSeconds"
  val KeyBrowserCacheTimeoutSeconds = s"$StaticContentRoot.browserCacheTimeoutSeconds"
  val KeyFileType = s"$StaticContentRoot.type"
}