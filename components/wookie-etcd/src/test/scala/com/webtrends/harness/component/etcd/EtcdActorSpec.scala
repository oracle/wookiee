package com.webtrends.harness.component.etcd

import java.util.concurrent.Future

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestProbe}
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.component.etcd.config.EtcdTestConfig
import net.nikore.etcd.EtcdJsonProtocol

import scala.util.Failure

class EtcdActorSpec extends EtcdTestBase {

  val system = ActorSystem("test", ConfigFactory.load(EtcdTestConfig.config))

  val probe = new TestProbe(system)
  val actor = TestActorRef[EtcdActor] (EtcdActor.props(EtcdSettings(system.settings.config.getConfig("wookie-etcd"))))

  Thread.sleep(2000)

  sequential

  "wookie-etcd actor" should {

    "be able to set key" in {
      probe.send(actor, SetKey("key1", "foo"))
      probe.expectMsgPF() {
        case r: Option[Boolean] =>
          r.get mustEqual true
      }
    }

    "be able to get a key and equal foo" in {
      probe.send(actor, GetKey("key1"))
      probe.expectMsgPF() {
        case r: Option[String] =>
          r.get mustEqual "foo"
      }
    }

    "be able to delete key" in {
      probe.send(actor, RemoveKey("key1"))
      probe.expectMsgPF() {
        case r: Option[Boolean] =>
          r.get mustEqual true
      }

    }

    /*"fail with a get key" in {
      probe.send(actor, GetKey("key1"))
      probe.expectMsgPF() {
        case Failure(t) =>
          throwA(t)
      }
    }*/
  }
}
