package com.oracle.infy.wookiee.discovery

import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.component.WookieeComponent
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.oracle.infy.wookiee.component.grpc.utils.TestModels
import com.oracle.infy.wookiee.discovery.command.{
  DiscoverableCommand,
  DiscoverableCommandExecution,
  DiscoverableCommandHelper
}
import com.oracle.infy.wookiee.test.{BaseWookieeTest, TestHarness}
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config
import io.grpc._
import org.apache.curator.test.TestingServer
import org.json4s.ext.JavaTimeSerializers
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._

case class TestInput(value: String)
case class TestOutput(value: String)

class TestDiscoverableCommand(override val name: String)(implicit val config: Config)
    extends DiscoverableCommand[TestInput, TestOutput] {

  override def execute(args: TestInput): Future[TestOutput] = {
    args.value match {
      case "outer-fail" => throw new IllegalArgumentException("outer-fail")
      case "inner-fail" => Future.failed(new IllegalStateException("inner-fail"))
      case _            => Future.successful(TestOutput(s"$name-${args.value}-output"))
    }
  }
}

class DiscoverableCommandSpec
    extends BaseWookieeTest
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with DiscoverableCommandExecution
    with DiscoverableCommandHelper {
  lazy val zkPort: Int = TestHarness.getFreePort
  lazy val grpcPort: Int = TestHarness.getFreePort
  lazy val zkServer: AtomicReference[TestingServer] = new AtomicReference(new TestingServer(zkPort))
  implicit lazy val testConfig: Config = testWookiee.config
  implicit val ec: ExecutionContext = ThreadUtil.createEC("test-discoverable-command-ec")
  implicit def format: Formats = DefaultFormats ++ JavaTimeSerializers.all

  val wasIntercepted: AtomicBoolean = new AtomicBoolean(false)
  override def config: Config = TestModels.conf(zkPort, grpcPort)

  val interceptor: ServerInterceptor = new ServerInterceptor {

    override def interceptCall[ReqT, RespT](
        call: ServerCall[ReqT, RespT],
        headers: Metadata,
        next: ServerCallHandler[ReqT, RespT]
    ): ServerCall.Listener[ReqT] = {
      wasIntercepted.set(true)
      next.startCall(call, headers)
    }
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    zkServer.get().start()

    registerDiscoverableCommand(new TestDiscoverableCommand("test-command-main"))
    DiscoverableCommandHelper.registerDiscoverableCommand(new TestDiscoverableCommand("test-command-main-2"))
    registerDiscoverableCommand(new TestDiscoverableCommand("test-command-token"), Some("test-bearer"))
    registerDiscoverableCommand(
      new TestDiscoverableCommand("test-command-interceptor"),
      None,
      List(
        interceptor
      ).asJava
    )

    GrpcManager.initializeGrpcNow(testConfig)
    GrpcManager.waitForManager(testConfig, waitForClean = true, 30)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    println("Shutting down Wookiee")
    testWookiee.stop()
    zkServer.get().stop()
  }

  override def componentMap: Option[Map[String, Class[_ <: WookieeComponent]]] =
    Some(
      Map(
        "wookiee-grpc-component" -> classOf[GrpcManager]
      )
    )

  "Discoverable Commands" should {
    val zkPath = testConfig.getString(s"${GrpcManager.ComponentName}.grpc.zk-discovery-path")

    "be able to be discovered and executed" in {
      val command = WookieeCommandExecutive.getMediator(testWookiee.getInstanceId).getCommand("test-command-main")
      command.isDefined mustBe true

      val result = Await.result(
        executeDiscoverableCommand[TestInput, TestOutput](
          zkPath,
          s"localhost:$zkPort",
          "not-used",
          None,
          "test-command-main",
          TestInput("input")
        ),
        15.seconds
      )
      result mustBe TestOutput("test-command-main-input-output")
    }

    "has support for auth tokens" in {
      val command = WookieeCommandExecutive.getMediator(testWookiee.getInstanceId).getCommand("test-command-token")
      command.isDefined mustBe true

      val result = Await.result(
        executeDiscoverableCommand[TestInput, TestOutput](
          zkPath,
          s"localhost:$zkPort",
          "test-bearer",
          None,
          "test-command-token",
          TestInput("input")
        ),
        15.seconds
      )
      result mustBe TestOutput("test-command-token-input-output")
      // Expect this to throw an error as token is wrong
      val exception = intercept[StatusRuntimeException](
        Await.result(
          executeDiscoverableCommand[TestInput, TestOutput](
            zkPath,
            s"localhost:$zkPort",
            "bad-bearer-token",
            None,
            "test-command-token",
            TestInput("input")
          ),
          15.seconds
        )
      )

      exception.getMessage mustEqual "UNAUTHENTICATED"
    }

    "has support for interceptors" in {
      val result = Await.result(
        executeDiscoverableCommand[TestInput, TestOutput](
          zkPath,
          s"localhost:$zkPort",
          "",
          None,
          "test-command-interceptor",
          TestInput("input")
        ),
        15.seconds
      )
      result mustBe TestOutput("test-command-interceptor-input-output")
      wasIntercepted.get() mustBe true
    }

    "have support for generic stubs that can take anything" in {
      val stub = getGenericStub(
        zkPath,
        s"localhost:$zkPort",
        "",
        None
      )

      val channel = GrpcManager.getChannelFromMediator(zkPath, s"localhost:$zkPort", "")
      stub.build(channel.managedChannel, CallOptions.DEFAULT)
    }

    "fail gracefully inside of the execute future" in {
      val ex = intercept[StatusRuntimeException] {
        Await.result(
          executeDiscoverableCommand[TestInput, TestOutput](
            zkPath,
            s"localhost:$zkPort",
            "",
            None,
            "test-command-main",
            TestInput("inner-fail")
          ),
          15.seconds
        )
      }

      ex.getMessage mustEqual "INTERNAL: Failed gRPC execution: 'inner-fail'"
    }

    "fail gracefully outside of the execute future" in {
      val ex = intercept[StatusRuntimeException] {
        Await.result(
          executeDiscoverableCommand[TestInput, TestOutput](
            zkPath,
            s"localhost:$zkPort",
            "",
            None,
            "test-command-main",
            TestInput("outer-fail")
          ),
          15.seconds
        )
      }

      ex.getMessage mustEqual "INTERNAL: Failed gRPC invocation: 'outer-fail'"
    }
  }
}
