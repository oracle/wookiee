package com.webtrends.harness.component.kafka

import java.net.InetAddress

import akka.actor.{Actor, ActorSystem}
import akka.testkit.TestProbe
import com.typesafe.config.Config
import com.webtrends.harness.component.kafka.config.KafkaTestConfig
import com.webtrends.harness.component.zookeeper.ZookeeperEvent.ZookeeperStateEventRegistration
import com.webtrends.harness.component.zookeeper.config.ZookeeperSettings
import com.webtrends.harness.component.zookeeper.{ZookeeperActor, ZookeeperService}
import org.apache.curator.test.TestingServer
import org.slf4j.LoggerFactory

import scala.util.control.Exception

object TestUtil {
  private val log = LoggerFactory.getLogger(getClass)


   def hostName = InetAddress.getLocalHost.getHostName
  /**
   * Simple retry
   * @param maxTries
   * @param fn
   * @tparam A
   * @return
   */

  def retry[A](maxTries:Int = 3, sleepMillis: Long = 100)(fn: => A): A ={
    @annotation.tailrec
    def _retry[A](n:Int, tries:Int)(fn: => A): A = {
      Exception.allCatch[A].either(fn) match {
        case Right(result) => result
        case Left(ex) if n >= (tries - 1) => throw ex
        case Left(ex) =>
          Thread.sleep(sleepMillis)
          _retry(n + 1, tries)(fn)
      }
    }

    _retry[A](0, maxTries)(fn)
  }

  object ZkHelper {
    def apply()(implicit system: ActorSystem) = new ZkHelper()
  }

  class ZkHelper(implicit system: ActorSystem) {
    val zkServer = new TestingServer()
    val c = KafkaTestConfig.zkConfig(zkServer.getConnectString)
    val zkActor = system.actorOf(ZookeeperActor.props(ZookeeperSettings(c.getConfig("wookiee-zookeeper"))), "zookeeperactor")
    /**
     * Helper to make sure zk is available
     */
    def ensureZkAvailable() = {
      //We need to make sure we can start zookeeper up before we start other actors
      retry() {
        ZookeeperService().register(Actor.noSender, ZookeeperStateEventRegistration(Actor.noSender))
        log.info("Zookeeper is READY")
      }
    }

    /**
     * Shutdown
     */
    def shutdown() = {
      system.stop(zkActor)
      zkServer.close()
    }
  }
}
