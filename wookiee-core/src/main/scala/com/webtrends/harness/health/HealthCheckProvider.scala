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
package com.webtrends.harness.health

import akka.actor.Actor
import akka.pattern._
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.logging.ActorLoggingAdapter
import com.webtrends.harness.service.messages.CheckHealth
import com.webtrends.harness.utils.ConfigUtil
import org.joda.time.DateTime

import java.util.jar.Attributes.Name
import java.util.jar.{Attributes, JarFile}
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

object HealthCheckProvider {

  /**
   * Rollup the overall status and critical alerts for each component
   * @param checks List of status objects for each component and service
   * @return Parent status that is only NOMRAL if all children were NORMAL
   */
  def rollupHealth(checks: Seq[HealthComponent]): ComponentHealth = {

    // Identify all components in the hierarchy in a DEGRADED or CRITICAL state
    def getAlertComponents(root: HealthComponent): Seq[ComponentHealth] = {
      if (root.state == ComponentState.CRITICAL || root.state == ComponentState.DEGRADED) {
        val rootCompHealth = ComponentHealth(root.state, s"${root.name}[${root.state}] - ${root.details}")
        rootCompHealth +: root.components.flatMap(getAlertComponents)
      } else {
        root.components.flatMap(getAlertComponents)
      }
    }

    val componentsToAlert = checks.flatMap(getAlertComponents)

    if (componentsToAlert.isEmpty) {
      ComponentHealth(ComponentState.NORMAL, "Thunderbirds are GO")
    } else {
      val status = if (componentsToAlert.exists(c => c.state == ComponentState.CRITICAL)) ComponentState.CRITICAL else ComponentState.DEGRADED
      val details = componentsToAlert.map(_.details)
      ComponentHealth(status, details.mkString("; "))
    }
  }

}

trait HealthCheckProvider {
  this: Actor with ActorLoggingAdapter =>
  val upTime = DateTime.now
  implicit val timeout =
    ConfigUtil.getDefaultTimeout(context.system.settings.config, HarnessConstants.KeyDefaultTimeout, Timeout(15 seconds))

  val scalaVersion = util.Properties.versionString
  val file = getClass.getProtectionDomain.getCodeSource.getLocation.getFile

  private def defaultAttributes(): java.util.jar.Manifest = {
    val man = new java.util.jar.Manifest()
    man.getMainAttributes.put(Name.IMPLEMENTATION_TITLE, "Webtrends Harness Service")
    man.getMainAttributes.put(Name.IMPLEMENTATION_VERSION, "develop-SNAPSHOT")
    man.getMainAttributes.put(new Attributes.Name("Implementation-Build"), "N/A")
    man
  }

  val manifest = file match {
    case _ if file.endsWith(".jar") =>
      Try(new JarFile(file).getManifest).getOrElse(defaultAttributes())
    case _ =>
      defaultAttributes()
  }

  val application = manifest.getMainAttributes.getValue(Name.IMPLEMENTATION_TITLE)
  val version = manifest.getMainAttributes.getValue(Name.IMPLEMENTATION_VERSION)

  /**
   * Run the health checks and return the current system state
   * @return
   */
  def runChecks: Future[ApplicationHealth] = {

    import context.dispatcher

    // Ask for the health of each component
    val future = (context.actorSelection(HarnessConstants.ActorPrefix) ? CheckHealth).mapTo[Seq[HealthComponent]]
    val p = Promise[ApplicationHealth]

    future.onComplete({
      case Success(checks) =>
        val overallHealth = HealthCheckProvider.rollupHealth(checks)
        p success ApplicationHealth(application, version, upTime, overallHealth.state, overallHealth.details, checks)
      case Failure(e) =>
        log.error("An error occurred while fetching the health request results", e)
        p success ApplicationHealth(application, version, upTime, ComponentState.CRITICAL, e.getMessage, Nil)
    })

    p.future
  }
}
