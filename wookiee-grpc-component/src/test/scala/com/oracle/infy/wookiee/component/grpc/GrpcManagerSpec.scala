package com.oracle.infy.wookiee.component.grpc

import cats.effect.unsafe.implicits.global
import com.google.protobuf.StringValue
import com.oracle.infy.wookiee.component.WookieeComponent
import com.oracle.infy.wookiee.component.grpc.GrpcManager.{CleanCheck, CleanResponse, GrpcDefinition}
import com.oracle.infy.wookiee.component.grpc.utils.TestModels
import com.oracle.infy.wookiee.component.grpc.utils.TestModels._
import com.oracle.infy.wookiee.grpc.WookieeGrpcUtils.DEFAULT_MAX_MESSAGE_SIZE
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils
import com.oracle.infy.wookiee.service.WookieeService
import com.oracle.infy.wookiee.test.{BaseWookieeTest, TestHarness, TestService}
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.retry.RetryForever
import org.apache.curator.test.TestingServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class GrpcManagerSpec
    extends BaseWookieeTest
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {
  lazy val zkPort: Int = TestHarness.getFreePort
  lazy val grpcPort: Int = TestHarness.getFreePort
  lazy val zkServer = new TestingServer(zkPort)
  zkServer.start()

  override def config: Config = TestModels.conf(zkPort, grpcPort)

  override protected def afterAll(): Unit = {
    super.afterAll()
    testWookiee.stop()
    zkServer.stop()
  }

  override def servicesMap: Option[Map[String, Class[_ <: WookieeService]]] =
    Some(
      Map(
        "testservice" -> classOf[TestService]
      )
    )

  override def componentMap: Option[Map[String, Class[_ <: WookieeComponent]]] =
    Some(
      Map(
        "wookiee-grpc-component" -> classOf[GrpcManager]
      )
    )

  "gRPC Manager" should {
    "load up fully" in zkPort.synchronized {
      val testComp = testWookiee.getComponentV2("wookiee-grpc-component")
      assert(testComp.isDefined, "gRPC Manager wasn't registered")

      testComp.foreach { tc =>
        val resp = ThreadUtil.awaitResponse[CleanResponse](tc, CleanCheck())
        resp.clean shouldEqual false
      }
    }

    "register a simple gRPC service" in zkPort.synchronized {
      GrpcManager.registerGrpcService(
        testWookiee.config,
        "manager-spec",
        List(
          new GrpcDefinition(new GrpcServiceOne().bindService()),
          new GrpcDefinition(new GrpcServiceTwo().bindService()),
          new GrpcDefinition(new GrpcServiceThree().bindService())
        )
      )
      GrpcManager.initializeGrpcNow(testWookiee.config)
      GrpcManager.waitForManager(testWookiee.config, waitForClean = true)

      val channel = GrpcManager.createChannel("/grpc/local_dev", s"localhost:$zkPort", "")
      try {
        val stub = new GrpcMockStub(channel.managedChannel)
        val resultOne = stub.sayHello(StringValue.of("msg1"), classOf[GrpcServiceOne].getSimpleName)
        val resultTwo = stub.sayHello(StringValue.of("msg2"), classOf[GrpcServiceTwo].getSimpleName)
        val resultThree = stub.sayHello(StringValue.of("msg3"), classOf[GrpcServiceThree].getSimpleName)

        resultOne.getValue shouldEqual "msg1:GrpcServiceOne"
        resultTwo.getValue shouldEqual "msg2:GrpcServiceTwo"
        resultThree.getValue shouldEqual "msg3:GrpcServiceThree"
      } finally channel.shutdown(true).unsafeRunSync()
    }

    "handle a message over the default 4MB with max-message-size configured" in zkPort.synchronized {
      GrpcManager.registerGrpcService(
        testWookiee.config,
        "message-size-spec",
        List(
          new GrpcDefinition(new GrpcServiceFour().bindService())
        )
      )
      GrpcManager.initializeGrpcNow(testWookiee.config)
      GrpcManager.waitForManager(testWookiee.config, waitForClean = true)

      val arraySize = 8000000 // ~8MB sized message
      val arrayBig = new Array[Char](arraySize)
      val stringBig = new String(arrayBig)

      val channel = GrpcManager.createChannel("/grpc/local_dev", s"localhost:$zkPort", "", None, 10000000)
      try {
        val stub = new GrpcMockStub(channel.managedChannel)
        ThreadUtil.awaitEvent({
          val resultBig = stub.sayHello(StringValue.of(stringBig), classOf[GrpcServiceFour].getSimpleName)
          resultBig.getValue.equals(s"$stringBig:GrpcServiceFour")
        })
      } finally channel.shutdown(true).unsafeRunSync()
    }

    "allow closing of curators in channels" in zkPort.synchronized {
      GrpcManager.registerGrpcService(
        testWookiee.config,
        "closing-spec",
        List(
          new GrpcDefinition(new GrpcServiceOne().bindService())
        )
      )
      GrpcManager.initializeGrpcNow(testWookiee.config)
      GrpcManager.waitForManager(testWookiee.config, waitForClean = true)

      val curator = GRPCUtils.curatorFramework(
        s"localhost:$zkPort",
        ThreadUtil.createEC(s"curator-blocking-${System.currentTimeMillis()}"),
        new RetryForever(5000)
      )
      curator.start()
      val channel = GrpcManager.createChannel("/grpc/local_dev", curator, "", None, DEFAULT_MAX_MESSAGE_SIZE)
      val stub = new GrpcMockStub(channel.managedChannel)
      val resultOne = stub.sayHello(StringValue.of("msg1"), classOf[GrpcServiceOne].getSimpleName)

      resultOne.getValue shouldEqual "msg1:GrpcServiceOne"
      channel.shutdown(true).unsafeRunSync()
      curator.getState shouldEqual CuratorFrameworkState.STOPPED
    }
  }
}
