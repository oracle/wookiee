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
package com.webtrends.harness.app

import akka.actor.{ActorRef, ActorSystem, Props, UnhandledMessage}
import akka.pattern._
import com.typesafe.config.Config
import com.webtrends.harness.UnhandledEventListener
import com.webtrends.harness.app.HarnessActor.ShutdownSystem
import com.webtrends.harness.logging.Logger

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class HarnessMeta(actorSystem: ActorSystem, harnessActor: ActorRef, config: Config)

/**
 * @author Spencer Wood
 */
object Harness {
  // Map from tcp port to Harness system and HarnessActor ref
  protected[harness] var harnessMap: Map[Int, HarnessMeta] = Map.empty
  def getActorSystem(port: Int = DEFAULT_PORT): Option[ActorSystem] = harnessMap.get(port).map(_.actorSystem)
  def getRootActor(port: Int = DEFAULT_PORT): Option[ActorRef] = harnessMap.get(port).map(_.harnessActor)

  protected[harness] var log: Logger = Logger(this.getClass)
  def getLogger: Logger = log
  val externalLogger: Logger = Logger.getLogger(this.getClass)

  val DEFAULT_PORT = 2552

  /**
   * Restart the specified actor system, if port = None will restart all
   */
  def restartActorSystem(port: Option[Int] = None): Unit = {
    port match {
      case Some(p) =>
        harnessMap.get(p) match {
          case Some(meta) =>
            externalLogger.info(s"Restarting the actor system for port $port")
            shutdownActorSystem(block = false, port) {
              startActorSystem(Some(meta.config), port)
            }
          case None =>
            externalLogger.info(s"There is no actor system for $p so starting up now")
            startActorSystem(None, port)
        }

      case None =>
        harnessMap foreach { case (p, meta) =>
          externalLogger.info(s"Restarting the actor system for port $p")
          shutdownActorSystem(block = false, Some(p)) {
            startActorSystem(Some(meta.config), Some(p))
          }
        }
    }
  }

  /**
   * Force a shutdown of the ActorSystem and the application's process, if port = None will shutdown all.
   */
  def shutdown(port: Option[Int] = None): Unit = {
    log.info("Shutting down Wookiee")
    new Thread("lifecycle") {
      override def run() {
        Thread.sleep(10)
        shutdownActorSystem(block = false, port) {
          System.exit(0)
        }
      }
    }.start()
  }

  /**
   * Start the actor system
   */
  def startActorSystem(config: Option[Config] = None, port: Option[Int] = None): Unit = harnessMap.synchronized {
    try {
      externalLogger.debug(s"Creating the actor system${port.map(p => s" for port $p").getOrElse("")}")
      val finalConfig = HarnessActorSystem.getConfig(config, port)
      val chosenPort = Try(finalConfig.getInt("akka.remote.netty.tcp.port")).getOrElse(DEFAULT_PORT)
      harnessMap.get(chosenPort).foreach { _ =>
        log.warn(s"Harness already registered on port $port, shutting down, try specifying different port next time")
        shutdown(Some(chosenPort))
      }

      val system = HarnessActorSystem(finalConfig)
      // add the unhandled message listener so we can debug messages easily that are not being handled
      val listener = system.actorOf(Props(new UnhandledEventListener))
      system.eventStream.subscribe(listener, classOf[UnhandledMessage])

      log = Logger(this.getClass, system)
      log.debug(s"Creating main Wookiee actor${port.map(p => s" for port $p").getOrElse("")}")
      implicit val sys: ActorSystem = system
      val rootActor = system.actorOf(HarnessActor.props, "system")
      harnessMap = harnessMap.updated(chosenPort, HarnessMeta(system, rootActor, finalConfig))
    } catch {
      case t: Throwable =>
        externalLogger.error(s"The actor system could not be started: ${t.getMessage}", t)
        sys.exit(0)
    }
  }

  /**
   * Shutdown the actor system
   */
  def shutdownActorSystem(block: Boolean, portOpt: Option[Int] = None)(f: => Unit): Unit = harnessMap.synchronized {
    log.debug(s"Shutting down the main actor${portOpt.map(p => s" for port $p").getOrElse("")}")
    import scala.concurrent.ExecutionContext.Implicits.global
    val port = portOpt.getOrElse(DEFAULT_PORT)

    // We will tell the main actor that we are shutting down. This allows it to shutdown
    // its children and perform any needed cleanup.
    getRootActor(port) foreach { root =>
      val fut = gracefulStop(root, 15.seconds, ShutdownSystem)
        .andThen {
          case Success(_) =>
            log.debug("Now shutting down the the system itself")
        }
        // Now shutdown the system
        .flatMap(_ => getActorSystem(port).map(_.terminate()).getOrElse(Future.successful(true)))

      fut.onComplete {
        case Success(_) =>
          // Remove from map of running Wookiees
          harnessMap = harnessMap - port
          externalLogger.debug("The actor system has terminated")
          // Call the passed function
          f
        case Failure(reason) =>
          log.error("We were unable to properly shutdown the main actor", reason)
          System.exit(0)
      }

      if (block) {
        Await.result(fut, 15.seconds)
      }
    }
  }

  /**
   * Add a shutdown hook so when the process is shut down that we cleanup cleanly, adds to all if port = None
   */
  def addShutdownHook(port: Option[Int] = None): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      getActorSystem(port.getOrElse(DEFAULT_PORT)) match {
        case Some(sys) =>
          sys.log.debug("The shutdown hook has been called")
          shutdownActorSystem(block = true) {
            externalLogger.info("Wookiee Shut Down, Thanks for Coming!")
          }
        case _ =>
      }
    }))
  }
}
