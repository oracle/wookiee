package com.webtrends.harness.component.kafka

import java.net.InetAddress

import akka.actor._
import akka.testkit.TestProbe
import com.webtrends.harness.component.kafka.KafkaConsumerCoordinator.{BroadcastToWorkers, TopicPartitionResp}
import com.webtrends.harness.component.kafka.actor.AssignmentDistributorLeader
import com.webtrends.harness.component.kafka.actor.KafkaTopicManager.TopicPartitionReq
import com.webtrends.harness.component.kafka.config.KafkaTestConfig
import com.webtrends.harness.component.zookeeper.{ZookeeperActor, ZookeeperService}
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import net.liftweb.json.Serialization
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.specs2.mutable.SpecificationLike
import org.specs2.runner.JUnitRunner
import org.specs2.time.NoTimeConversions

import scala.collection.mutable
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class ConsumerDistributorSpec
  extends SpecificationLike with NoTimeConversions
{
  private val log = LoggerFactory.getLogger(getClass)

  import AssignmentDistributorLeader._
  import TestUtil.ZkHelper
  import KafkaConsumerDistributor._
  val b0 = "broker0"
  val b1 = "broker1"
  val topic = "scsRawHits"
  val topicPartData = Set(PartitionAssignment(topic, 0, "G", b0), PartitionAssignment(topic, 1, "G", b0),
    PartitionAssignment(topic, 2, "G", b0), PartitionAssignment(topic, 3, "G", b0), PartitionAssignment(topic, 0, "G", b1),
    PartitionAssignment(topic, 1, "G", b1), PartitionAssignment(topic, 2, "G", b1))

  //We might need to disable if this fails on the jenkins server
  args(skipAll = false, sequential = true)

  implicit val system = ActorSystem("test", KafkaTestConfig.config)
  implicit val timeout = 10 seconds
  val zkHelper = ZkHelper()

  zkHelper.ensureZkAvailable()

  Thread.sleep(2000)

  val hostName = TestUtil.hostName

  val kafkaProxy = system.actorOf { Props {
      new Actor with ActorLogging {
        override def receive: Receive = {
          case TopicPartitionReq  => sender ! TopicPartitionResp(topicPartData)

          case msg => log.info(s"Got messages not expecting: $msg")
        }
      }
    }
  }

  val distributor = system.actorOf(KafkaConsumerDistributor.props(kafkaProxy),
                                "consumer-distributor")
  val coordinator = system.actorOf(KafkaConsumerCoordinator.props(kafkaProxy),
                                "coordinator")


  val duration = 5 seconds

  "distributor " should {

    "be able to register self in zk" in {
      val probe = TestProbe()

      val result = TestUtil.retry(5, 550) {
        probe.send(distributor, CheckHealth)
        val result = probe.receiveOne(duration).asInstanceOf[HealthComponent]
        log.debug(s"Result $result")
        require(result.state == ComponentState.NORMAL)
        result
      }

      result.state must beEqualTo(ComponentState.NORMAL)
    }


    "be able to get registered nodes" in {

      val probe = TestProbe()
      probe.send(distributor, GetRegisteredNodes)

      val result = probe.receiveOne(duration).asInstanceOf[RegisteredNodes]

      result.data must beSome
      log.debug(s"Registered nodes ${result.data.get}")
      result.data.get.size must beEqualTo(1)
    }


    "be able to get a leader" in {
      val probe = TestProbe()

      val leader = TestUtil.retry(5) {
        probe.send(distributor, GetAssignmentLeader)

        val lr = probe.receiveOne(duration).asInstanceOf[AssignmentLeaderResult]

        require(lr.leader.nonEmpty)
        lr
      }

      leader.leader must beSome
    }

    "be able to fetch some assignments " in {
      val probe = TestProbe()


      val assignments = TestUtil.retry(5) {
        probe.send(distributor, GetNodeAssignments)
        val a = probe.receiveOne(duration).asInstanceOf[NodeAssignments]
        require(a.data.nonEmpty && a.data.get.nonEmpty)
        a
      }

      assignments.data must beSome
      log.debug(s"Assignments ${assignments.data.get}")

      assignments.data.get must haveKey(hostName)

      val json = assignments.data.get(hostName)

      val partAssignments = Serialization.read(json)(formats, partsManifest)

      val expected = topicPartData.size

      partAssignments must have size expected
    }

  }

  "coordinator " should {

    "be able to send a broadcast " in {
      val probe = TestProbe()

      probe.send(coordinator, BroadcastToWorkers(Identify("1")))

      val results = probe.receiveN(topicPartData.size, timeout).asInstanceOf[Seq[ActorIdentity]]

      log.debug(s"Coordinator results ${results}")
      results foreach { ai =>
        ai.correlationId.asInstanceOf[String] must beEqualTo("1")
        ai.ref must beSome;
      }
      success
    }
  }


  step {
    system.stop(distributor)
    system.stop(coordinator)
    system.stop(kafkaProxy)
    zkHelper.shutdown
    system.shutdown()
  }

}
