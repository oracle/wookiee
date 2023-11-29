package com.oracle.infy.wookiee.kafka.testing.model

import org.apache.curator.test.{InstanceSpec, TestingCluster, TestingServer}

import java.util
import scala.jdk.CollectionConverters._

trait ZooMode {
  def getConnectString: String
  def close(): Unit
}

// Starts itself on instantiation
case class TestingServerMode(port: Int) extends ZooMode {
  private val zkServer: TestingServer = new TestingServer(port)
  zkServer.start()

  override def getConnectString: String = zkServer.getConnectString
  override def close(): Unit = zkServer.close()
}

object TestingServerMode {
  def apply(): TestingServerMode = TestingServerMode(-1) // -1 = random port
}

// Starts itself on instantiation
case class TestingClusterMode(cluster: TestingCluster) extends ZooMode {
  private val zkCluster = cluster
  zkCluster.start()

  override def getConnectString: String = zkCluster.getConnectString
  override def close(): Unit = zkCluster.close()
}

object TestingClusterMode {

  def apply(instanceQty: Int): TestingClusterMode =
    TestingClusterMode(new TestingCluster(instanceQty))

  def apply(ports: Array[Int]): TestingClusterMode = {
    val customInstanceSpecProps = new util.HashMap[String, Object]()
    customInstanceSpecProps.put("admin.enableServer", false.asInstanceOf[Object]) //scalafix:ok

    val specs: util.List[InstanceSpec] =
      ports
        .zipWithIndex
        .map(
          (item: (Int, Int)) =>
            new InstanceSpec(
              null, //scalafix:ok
              item._1,
              -1, // random port
              -1, // random port
              false,
              item._2 + 1,
              -1,
              -1,
              customInstanceSpecProps
            )
        )
        .toList
        .asJava

    TestingClusterMode(new TestingCluster(specs))
  }
}
