package com.oracle.infy.wookiee.component.zookeeper

import org.apache.curator.x.discovery.details.InstanceProvider
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util
import java.util.UUID
import scala.jdk.CollectionConverters._

class WookieeWeightedStrategySpec extends AnyWordSpecLike with Matchers {

  class MockInstanceProvider(instances: Seq[ServiceInstance[WookieeServiceDetails]])
      extends InstanceProvider[WookieeServiceDetails] {
    override def getInstances: util.List[ServiceInstance[WookieeServiceDetails]] = instances.toList.asJava
  }

  def builderInstance(id: Int, weight: Int): ServiceInstance[WookieeServiceDetails] =
    ServiceInstance
      .builder[WookieeServiceDetails]()
      .uriSpec(new UriSpec(s"akka.tcp://server@localhost:8080/"))
      .id(id.toString)
      .name(UUID.randomUUID().toString)
      .payload(new WookieeServiceDetails(weight))
      .port(8080)
      .build()

  "WookieeWeightedStrategy" should {

    "returns null when no instances" in {
      val instances = Seq.empty[ServiceInstance[WookieeServiceDetails]]
      val instanceProvider = new MockInstanceProvider(instances)
      val strategy = new WookieeWeightedStrategy()

      strategy.getInstance(instanceProvider) shouldBe null
    }

    "default to round-robin when weights are all the same" in {
      val instances = (0 to 10).map(i => builderInstance(i, 0))
      val instanceProvider = new MockInstanceProvider(instances)
      val strategy = new WookieeWeightedStrategy()

      (0 to 10).map(i => strategy.getInstance(instanceProvider).getId == i.toString).reduce(_ && _) shouldBe true
    }

    "pick the lowest weighted instance" in {
      val instances = (1 to 10).map(i => builderInstance(i, i)) ++ Seq(builderInstance(0, 0))
      val instanceProvider = new MockInstanceProvider(instances)
      val strategy = new WookieeWeightedStrategy()

      strategy.getInstance(instanceProvider).getId shouldBe "0"
    }

    "pick the lowest as weight changes" in {
      val instances = (10 to 20).map(i => builderInstance(i, i)) ++ Seq(builderInstance(5, 5))
      val instanceProvider = new MockInstanceProvider(instances)

      val strategy = new WookieeWeightedStrategy()

      // first check prior to updated instance weights has lowest 5
      strategy.getInstance(instanceProvider).getId shouldBe "5"

      // second check after weight for instance 5 has increased and now id 10 is lowest
      val updatedInstances = (10 to 20).map(i => builderInstance(i, i)) ++ Seq(builderInstance(5, 15))
      val updatedProvider = new MockInstanceProvider(updatedInstances)
      strategy.getInstance(updatedProvider).getId shouldBe "10"
    }

  }
}
