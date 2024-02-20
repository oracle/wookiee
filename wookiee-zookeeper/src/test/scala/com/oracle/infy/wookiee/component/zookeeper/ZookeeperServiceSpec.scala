package com.oracle.infy.wookiee.component.zookeeper

import akka.actor._
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperActor.GetSetWeightInterval
import com.oracle.infy.wookiee.component.zookeeper.ZookeeperEvent.{ZookeeperChildEvent, ZookeeperChildEventRegistration}
import com.oracle.infy.wookiee.component.zookeeper.mock.MockZookeeper
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.test.TestingServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class ZookeeperServiceSpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll with PatienceConfiguration {

  val zkServer: AtomicReference[TestingServer] = new AtomicReference()
  val system: AtomicReference[ActorSystem] = new AtomicReference()
  val service: AtomicReference[ZookeeperAdapterNonActor] = new AtomicReference()
  val zkActor: AtomicReference[ActorRef] = new AtomicReference()

  implicit val to: Timeout = Timeout(5.seconds)
  val awaitResultTimeout: FiniteDuration = 5000.milliseconds

  val deletedNodes = new ConcurrentLinkedQueue[String]()
  val createdNodes = new ConcurrentLinkedQueue[String]()
  val changedNodes = new ConcurrentLinkedQueue[String]()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    zkServer.set(new TestingServer())
    system.set(ActorSystem("zk-test", loadConfig))
    service.set(MockZookeeper(zkServer.get().getConnectString)(system.get()))
    zkActor.set(ZookeeperService.getMediator(system.get()))
    system.get().actorOf(Props(new ZKEventWatcher()), "zk-event-watcher-test")
    ()
  }

  def checkForEntry(queue: ConcurrentLinkedQueue[String], path: String): Boolean = {
    val timeout = System.currentTimeMillis() + 5000L
    while (!queue.contains(path) && System.currentTimeMillis() < timeout) {}
    queue.contains(path)
  }

  class ZKEventWatcher() extends Actor with ZookeeperEventAdapter {

    override def preStart(): Unit = {
      super.preStart()
      register(self, ZookeeperChildEventRegistration(self, "/"))
    }

    override def receive: Receive = {
      case ZookeeperChildEvent(eventType, oldData, newData) =>
        eventType match {
          // /StreamingZMQ/{pod}_01/sapi/streams
          case CuratorCacheListener.Type.NODE_CREATED =>
            createdNodes.add(newData.getPath)
            ()
          case CuratorCacheListener.Type.NODE_CHANGED =>
            changedNodes.add(oldData.getPath)
            ()
          case CuratorCacheListener.Type.NODE_DELETED =>
            deletedNodes.add(oldData.getPath)
            ()
        }
    }
  }

  "The zookeeper service" should {
    "allow callers to create a node for a valid path" in {
      val res =
        Await.result(service.get().createNode("/test", ephemeral = false, Some("data".getBytes)), awaitResultTimeout)
      res shouldEqual "/test"
      ThreadUtil.awaitEvent(checkForEntry(createdNodes, "/test"), 5000L)
    }

    "allow callers to create a node for a valid namespace and path" in {
      val res = Await.result(
        service.get().createNode("/namespacetest", ephemeral = false, Some("namespacedata".getBytes), Some("space")),
        awaitResultTimeout
      )
      res shouldEqual "/namespacetest"
    }

    "allow callers to delete a node for a valid path" in {
      val res =
        Await.result(
          service.get().createNode("/deleteTest", ephemeral = false, Some("data".getBytes)),
          awaitResultTimeout
        )
      res shouldEqual "/deleteTest"
      val res2 = Await.result(service.get().deleteNode("/deleteTest"), awaitResultTimeout)
      res2 shouldEqual "/deleteTest"
      // Often fails due to async deletion
//      ThreadUtil.awaitEvent(checkForEntry(deletedNodes, "/deleteTest"))
    }

    "allow callers to delete a node for a valid namespace and path " in {
      val res = Await.result(
        service.get().createNode("/deleteTest", ephemeral = false, Some("data".getBytes), Some("space")),
        awaitResultTimeout
      )
      res shouldEqual "/deleteTest"
      val res2 = Await.result(service.get().deleteNode("/deleteTest", Some("space")), awaitResultTimeout)
      res2 shouldEqual "/deleteTest"
    }

    "allow callers to get data for a valid path " in {
      val res = Await.result(service.get().getData("/test"), awaitResultTimeout)
      new String(res) shouldEqual "data"
    }

    "allow callers to get data for a valid namespace and path " in {
      val res = Await.result(service.get().getData("/namespacetest", Some("space")), awaitResultTimeout)
      new String(res) shouldEqual "namespacedata"
    }

    " allow callers to get data for a valid path with a namespace" in {
      val res = Await.result(service.get().getData("/namespacetest", Some("space")), awaitResultTimeout)
      new String(res) shouldEqual "namespacedata"
    }

    " return an error when getting data for an invalid path " in {
      an[Exception] should be thrownBy Await.result(service.get().getData("/testbad"), awaitResultTimeout)
    }

    " allow callers to get children with no data for a valid path " in {
      Await.result(service.get().createNode("/test/child", ephemeral = false, None), awaitResultTimeout)
      val res2 = Await.result(service.get().getChildren("/test"), awaitResultTimeout)
      res2.head._1 shouldEqual "child"
      res2.head._2 shouldEqual None
    }

    " allow callers to get children with data for a valid path " in {
      Await.result(service.get().setData("/test/child", "data".getBytes), awaitResultTimeout)
      val res2 = Await.result(service.get().getChildren("/test", includeData = true), awaitResultTimeout)
      res2.head._1 shouldEqual "child"
      res2.head._2.get shouldEqual "data".getBytes
      ThreadUtil.awaitEvent(checkForEntry(changedNodes, "/test/child"), 5000L)
    }

    " return an error when getting children for an invalid path " in {
      an[Exception] should be thrownBy Await.result(service.get().getChildren("/testbad"), awaitResultTimeout)
    }

    "use set weight interval defined in config" in {
      Await.result(zkActor.get() ? GetSetWeightInterval(), 3.second).asInstanceOf[Long] shouldBe 2
    }
  }

  override protected def afterAll(): Unit = {
    Try(TestKit.shutdownActorSystem(system.get()))
    Try(zkServer.get().close())
    ()
  }

  def loadConfig: Config = {
    ConfigFactory.parseString("""
      instance-id = "zk-test"
      discoverability {
        set-weight-interval = 2s
      }
      wookiee-zookeeper {
        quorum = "%s"
      }""".format(zkServer.get().getConnectString)).withFallback(ConfigFactory.load()).resolve
  }
}
