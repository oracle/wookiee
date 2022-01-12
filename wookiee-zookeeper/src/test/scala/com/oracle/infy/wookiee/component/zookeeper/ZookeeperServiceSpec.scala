package com.oracle.infy.wookiee.component.zookeeper

import akka.actor._
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperActor.GetSetWeightInterval
import com.oracle.infy.wookiee.component.zookeeper.discoverable.DiscoverableService.{
  MakeDiscoverable,
  QueryForInstances,
  UpdateWeight
}
import com.oracle.infy.wookiee.component.zookeeper.mock.MockZookeeper
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.curator.test.TestingServer
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually._
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._

class ZookeeperServiceSpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll with PatienceConfiguration {

  val zkServer = new TestingServer()
  implicit val system: ActorSystem = ActorSystem("test", loadConfig)
  val service: ZookeeperAdapterNonActor = MockZookeeper(zkServer.getConnectString)
  val zkActor: ActorRef = ZookeeperService.getZkActor.get

  implicit val to: Timeout = Timeout(5.seconds)
  val awaitResultTimeout: FiniteDuration = 5000.milliseconds

  "The zookeeper service" should {
    "allow callers to create a node for a valid path" in {
      val res = Await.result(service.createNode("/test", ephemeral = false, Some("data".getBytes)), awaitResultTimeout)
      res shouldEqual "/test"
    }

    "allow callers to create a node for a valid namespace and path" in {
      val res = Await.result(
        service.createNode("/namespacetest", ephemeral = false, Some("namespacedata".getBytes), Some("space")),
        awaitResultTimeout
      )
      res shouldEqual "/namespacetest"
    }

    "allow callers to delete a node for a valid path" in {
      val res =
        Await.result(service.createNode("/deleteTest", ephemeral = false, Some("data".getBytes)), awaitResultTimeout)
      res shouldEqual "/deleteTest"
      val res2 = Await.result(service.deleteNode("/deleteTest"), awaitResultTimeout)
      res2 shouldEqual "/deleteTest"
    }

    "allow callers to delete a node for a valid namespace and path " in {
      val res = Await.result(
        service.createNode("/deleteTest", ephemeral = false, Some("data".getBytes), Some("space")),
        awaitResultTimeout
      )
      res shouldEqual "/deleteTest"
      val res2 = Await.result(service.deleteNode("/deleteTest", Some("space")), awaitResultTimeout)
      res2 shouldEqual "/deleteTest"
    }

    "allow callers to get data for a valid path " in {
      val res = Await.result(service.getData("/test"), awaitResultTimeout)
      new String(res) shouldEqual "data"
    }

    "allow callers to get data for a valid namespace and path " in {
      val res = Await.result(service.getData("/namespacetest", Some("space")), awaitResultTimeout)
      new String(res) shouldEqual "namespacedata"
    }

    " allow callers to get data for a valid path with a namespace" in {
      val res = Await.result(service.getData("/namespacetest", Some("space")), awaitResultTimeout)
      new String(res) shouldEqual "namespacedata"
    }

    " return an error when getting data for an invalid path " in {
      an[Exception] should be thrownBy Await.result(service.getData("/testbad"), awaitResultTimeout)
    }

    " allow callers to get children with no data for a valid path " in {
      Await.result(service.createNode("/test/child", ephemeral = false, None), awaitResultTimeout)
      val res2 = Await.result(service.getChildren("/test"), awaitResultTimeout)
      res2.head._1 shouldEqual "child"
      res2.head._2 shouldEqual None
    }

    " allow callers to get children with data for a valid path " in {
      Await.result(service.setData("/test/child", "data".getBytes), awaitResultTimeout)
      val res2 = Await.result(service.getChildren("/test", includeData = true), awaitResultTimeout)
      res2.head._1 shouldEqual "child"
      res2.head._2.get shouldEqual "data".getBytes
    }

    " return an error when getting children for an invalid path " in {
      an[Exception] should be thrownBy Await.result(service.getChildren("/testbad"), awaitResultTimeout)
    }

    "allow callers to discover commands " in {
      val res =
        Await.result(zkActor ? MakeDiscoverable("base/path", "testname", new UriSpec("file://foo")), awaitResultTimeout)
      res.asInstanceOf[Boolean] shouldBe true
    }

    "have default weight set to 0" in {
      val basePath = "base/path"
      val name = UUID.randomUUID().toString

      Await.result(zkActor ? MakeDiscoverable(basePath, name, new UriSpec("file://foo")), awaitResultTimeout)

      val res2 = Await.result(zkActor ? QueryForInstances(basePath, name), awaitResultTimeout)
      res2.asInstanceOf[List[ServiceInstance[WookieeServiceDetails]]].head.getPayload.getWeight shouldBe 0
    }

    "update weight " in {
      val basePath = "base/path"
      val name = UUID.randomUUID().toString

      Await.result(zkActor ? MakeDiscoverable(basePath, name, new UriSpec("file://foo")), awaitResultTimeout)
      Await.result(zkActor ? UpdateWeight(100, basePath, name, forceSet = false), awaitResultTimeout)

      def result: List[ServiceInstance[WookieeServiceDetails]] = {
        val r = Await.result(zkActor ? QueryForInstances(basePath, name), awaitResultTimeout)
        r.asInstanceOf[List[ServiceInstance[WookieeServiceDetails]]]
      }

      eventually(timeout(3.seconds), interval(100.milliseconds)) { result.head.getPayload.getWeight shouldBe 100 }
    }

    "update weight in zookeeper right away if forceSet is true" in {
      val basePath = "base/path"
      val name = UUID.randomUUID().toString

      Await.result(zkActor ? MakeDiscoverable(basePath, name, new UriSpec("file://foo")), awaitResultTimeout)
      Await.result(zkActor ? UpdateWeight(100, basePath, name, forceSet = true), awaitResultTimeout)

      val res = Await
        .result(zkActor ? QueryForInstances(basePath, name), awaitResultTimeout)
        .asInstanceOf[List[ServiceInstance[WookieeServiceDetails]]]

      res.head.getPayload.getWeight shouldBe 100
    }

    "not update weight in zookeeper right away if forceSet is false" in {
      val basePath = "base/path"
      val name = UUID.randomUUID().toString

      Await.result(zkActor ? MakeDiscoverable(basePath, name, new UriSpec("file://foo")), awaitResultTimeout)
      Await.result(zkActor ? UpdateWeight(100, basePath, name, forceSet = false), awaitResultTimeout)

      val res = Await
        .result(zkActor ? QueryForInstances(basePath, name), awaitResultTimeout)
        .asInstanceOf[List[ServiceInstance[WookieeServiceDetails]]]

      res.head.getPayload.getWeight shouldBe 0
    }

    "update weight on a set interval " in {
      val basePath = "base/path"
      val name = UUID.randomUUID().toString

      Await.result(zkActor ? MakeDiscoverable(basePath, name, new UriSpec("file://foo")), awaitResultTimeout)
      Await.result(zkActor ? UpdateWeight(100, basePath, name, forceSet = false), awaitResultTimeout)

      Thread.sleep(3000)

      val res = Await
        .result(zkActor ? QueryForInstances(basePath, name), awaitResultTimeout)
        .asInstanceOf[List[ServiceInstance[WookieeServiceDetails]]]

      res.head.getPayload.getWeight shouldBe 100
    }

    "use set weight interval defined in config" in {
      Await.result(zkActor ? GetSetWeightInterval(), 3.second).asInstanceOf[Long] shouldBe 2
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    zkServer.close()
  }

  def loadConfig: Config = {
    ConfigFactory.parseString("""
      discoverability {
        set-weight-interval = 2s
      }
      wookiee-zookeeper {
        quorum = "%s"
      }""".format(zkServer.getConnectString)).withFallback(ConfigFactory.load()).resolve
  }
}
