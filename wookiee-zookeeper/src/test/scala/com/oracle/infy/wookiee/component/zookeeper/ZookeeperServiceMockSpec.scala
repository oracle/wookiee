package com.oracle.infy.wookiee.component.zookeeper

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperService.{GetRegistrationPath, getMediator}
import com.oracle.infy.wookiee.test.TestHarness
import com.typesafe.config.ConfigFactory
import org.apache.zookeeper.KeeperException.NoNodeException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._

class ZookeeperServiceMockSpec
    extends AnyWordSpecLike
    with Matchers
    with ZookeeperAdapterNonActor
    with BeforeAndAfterAll {

  val testHarness: TestHarness = TestHarness(ConfigFactory.parseString("""
      |wookiee-zookeeper {
      |  enabled = true
      |  mock-enabled = true
      |  mock-port = 59595
      |  base-path = "/test_path"
      |  register-self = false
      |}
    """.stripMargin))
  override implicit val zkActorSystem: ActorSystem = testHarness.system

  implicit val to: Timeout = Timeout(5.seconds)
  val awaitResultTimeout: FiniteDuration = 5000.milliseconds

  "The zookeeper mock service" should {
    "don't register self when not set to" in {
      val startPath =
        Await.result((getMediator(zkActorSystem) ? GetRegistrationPath()).mapTo[String], awaitResultTimeout)
      try {
        val res = Await.result(getData(startPath, None), awaitResultTimeout)
        new String(res) shouldEqual "should not be set"
      } catch {
        case _: NoNodeException =>
          log.info(s"No node registered for [$startPath] as expected")
          true shouldEqual true
      }
    }

    "allow callers to create a node for a valid path" in {
      val res = Await.result(createNode("/test", ephemeral = false, Some("data".getBytes)), awaitResultTimeout)
      res shouldEqual "/test"
    }

    "allow callers to create a node for a valid namespace and path" in {
      val res = Await.result(
        createNode("/namespacetest", ephemeral = false, Some("namespacedata".getBytes), Some("space")),
        awaitResultTimeout
      )
      res shouldEqual "/namespacetest"
    }

    "allow callers to delete a node for a valid path" in {
      val res = Await.result(createNode("/deleteTest", ephemeral = false, Some("data".getBytes)), awaitResultTimeout)
      res shouldEqual "/deleteTest"
      val res2 = Await.result(deleteNode("/deleteTest"), awaitResultTimeout)
      res2 shouldEqual "/deleteTest"
    }

    "allow callers to delete a node for a valid namespace and path " in {
      val res = Await.result(
        createNode("/deleteTest", ephemeral = false, Some("data".getBytes), Some("space")),
        awaitResultTimeout
      )
      res shouldEqual "/deleteTest"
      val res2 = Await.result(deleteNode("/deleteTest", Some("space")), awaitResultTimeout)
      res2 shouldEqual "/deleteTest"
    }

    "allow callers to get data for a valid path " in {
      val res = Await.result(getData("/test"), awaitResultTimeout)
      new String(res) shouldEqual "data"
    }

    "allow callers to get data for a valid namespace and path " in {
      val res = Await.result(getData("/namespacetest", Some("space")), awaitResultTimeout)
      new String(res) shouldEqual "namespacedata"
    }

    " allow callers to get data for a valid path with a namespace" in {
      val res = Await.result(getData("/namespacetest", Some("space")), awaitResultTimeout)
      new String(res) shouldEqual "namespacedata"
    }

    " return an error when getting data for an invalid path " in {
      an[Exception] should be thrownBy Await.result(getData("/testbad"), awaitResultTimeout)
    }

    " allow callers to get children with no data for a valid path " in {
      Await.result(createNode("/test/child", ephemeral = false, None), awaitResultTimeout)
      val res2 = Await.result(getChildren("/test"), awaitResultTimeout)
      res2.head._1 shouldEqual "child"
      res2.head._2 shouldEqual None
    }

    " allow callers to get children with data for a valid path " in {
      Await.result(setData("/test/child", "data".getBytes), awaitResultTimeout)
      val res2 = Await.result(getChildren("/test", includeData = true), awaitResultTimeout)
      res2.head._1 shouldEqual "child"
      res2.head._2.get shouldEqual "data".getBytes
    }

    " return an error when getting children for an invalid path " in {
      an[Exception] should be thrownBy Await.result(getChildren("/testbad"), awaitResultTimeout)
    }

    "allow callers to create atomic longs " in {
      val res = Await.result(createCounter("/test/counter"), awaitResultTimeout)
      val res2 = Await.result(createCounter("/test/counter"), awaitResultTimeout)
      val res3 = Await.result(createCounter("/test/counter3"), awaitResultTimeout)

      res.increment()
      res2.increment()
      res3.increment()
      res.increment()
      res2.get().postValue() shouldEqual 3
      res.get().postValue() shouldEqual 3
      res3.get().postValue() shouldEqual 1
    }
  }

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(zkActorSystem)
}
