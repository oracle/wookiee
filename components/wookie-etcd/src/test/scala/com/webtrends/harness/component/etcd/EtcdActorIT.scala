package com.webtrends.harness.component.etcd

import akka.testkit.{TestActorRef, TestProbe}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class EtcdActorIT extends EtcdTestBase {

  val probe = new TestProbe(actorSystem)
  val actor = TestActorRef[EtcdActor] (EtcdActor.props(EtcdSettings(actorSystem.settings.config.getConfig("wookie-etcd"))))

  Thread.sleep(2000)

  sequential

  "wookie-etcd actor" should {

    "be able to set key" in {
      probe.send(actor, SetKey("key1", "foo"))
      probe.expectMsgPF() {
        case r: Boolean =>
          r mustEqual true
      }
    }

    "be able to get a key and equal foo" in {
      probe.send(actor, GetKey("key1"))
      probe.expectMsgPF() {
        case r: String =>
          r mustEqual "foo"
      }
    }

    "be able to delete key" in {
      probe.send(actor, RemoveKey("key1"))
      probe.expectMsgPF() {
        case r: Boolean =>
          r mustEqual true
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
