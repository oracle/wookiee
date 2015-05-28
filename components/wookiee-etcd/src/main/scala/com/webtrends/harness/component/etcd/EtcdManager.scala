/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:16 PM
 */
package com.webtrends.harness.component.etcd

import akka.pattern.ask
import com.webtrends.harness.component.Component
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.utils.ConfigUtil

import scala.concurrent._
import scala.util.{Failure, Success}

class EtcdManager(name:String) extends Component(name) with Etcd {

  implicit val etcdSettings = EtcdSettings(ConfigUtil.prepareSubConfig(config, name))

  override protected def defaultChildName: Option[String] = Some(Etcd.EtcdName)


  /**
   * Start function will start any child actors that will be managed by the ComponentManager
   * @return
   */
  override def start = {
    startEtcdComponent
    super.start
  }

  /**
   * Stop will execute any cleanup work to be done for the child actors
   * if not necessary this can be deleted
   * @return
   */
  override def stop = {
    stopEtcd
    super.stop
  }

  object EtcdManager {
    val ComponentName = "wookiee-etcd"
  }

  /**
   * The actor has been asked to respond with some health information. It needs
   * to implement this function and provide a list of components used in this service
   * and their current state. By default the health check will simply run through all the
   * children for the actor and get their health. Should be overridden for any custom
   * behavior
   * @return An instance of a health component
   */
  override def checkHealth: Future[HealthComponent] = {

    import scala.concurrent.ExecutionContext.Implicits.global

    val p = Promise[HealthComponent]
    if (EtcdRef == None) {
      p success HealthComponent(self.path.name, ComponentState.CRITICAL, "Etcd is not connected or started.")
    } else {

      val check: Future[String] = ask(EtcdRef.get, ListDir("/")).mapTo[String]

      check onComplete {
        case Success(result) =>
          val comp = HealthComponent(self.path.name, ComponentState.NORMAL, "Etcd is running.")
          p success comp
        case Failure(f) => p success HealthComponent(self.path.name, ComponentState.CRITICAL, f.getMessage)
      }
    }
    p.future
  }
}