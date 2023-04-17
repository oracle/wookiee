//package com.oracle.infy.wookiee.test.extension
//
//import akka.actor.{ActorRef, ActorSystem, Props}
//import akka.pattern.ask
//import akka.util.Timeout
//import com.oracle.infy.wookiee.app.{HarnessActorSystem, HarnessClassLoader}
//import com.oracle.infy.wookiee.component.{
//  ComponentManager,
//  ComponentRequest,
//  ComponentResponse,
//  GetComponent,
//  LoadComponent,
//  ReloadComponent,
//  Request
//}
//import com.oracle.infy.wookiee.service.HawkClassLoader
//import com.oracle.infy.wookiee.test.{BaseWookieeTest, TestHarness}
//import com.typesafe.config.{Config, ConfigFactory}
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AnyWordSpecLike
//
//import java.io.File
//import java.net.URLClassLoader
//import scala.concurrent.Await
//import scala.concurrent.duration._
//import scala.util.Try
//
//// Note: This test only works if you set your working directory to wookiee or wookiee-test
//class ClassLoaderSpec extends BaseWookieeTest with AnyWordSpecLike with Matchers {
//  implicit val timeout: Timeout = 25.seconds
//
//  "A single class loader" should {
//    "Take the first of the same package classes across different jars by default" in {
//      val sys = ActorSystem("SingleLoader", TestHarness.defaultConfig)
//
//      try {
//        val jarA = getClass.getResource("/basic-extension.jar")
//        val jarB = getClass.getResource("/second-extension.jar")
//        val harnessClassLoader = new HarnessClassLoader(new URLClassLoader(Array(jarA, jarB)))
//        val cm = sys.actorOf(Props[ComponentManager]())
//
//        println("Loading each component jar..")
//        val extA = Await.result(
//          (cm ? LoadComponent("BasicExtensionA", "com.oracle.infy.wookiee.qa.BasicExtension", Some(harnessClassLoader)))
//            .mapTo[Option[ActorRef]],
//          timeout.duration
//        )
//        val extB = Await.result(
//          (cm ? LoadComponent("BasicExtensionB", "com.oracle.infy.wookiee.qa.BasicExtension", Some(harnessClassLoader)))
//            .mapTo[Option[ActorRef]],
//          timeout.duration
//        )
//        Thread.sleep(1000L)
//        println("\nTrying to log from each..")
//        val instA = extA.map(act => Await.result((act ? "log").mapTo[String], timeout.duration)).getOrElse("")
//        val instB = extB.map(act => Await.result((act ? "log").mapTo[String], timeout.duration)).getOrElse("")
//
//        instA shouldEqual instB
//      } finally {
//        sys.terminate()
//        ()
//      }
//    }
//  }
//
//  "A class loader for each component" should {
//    "Keep separate classes, even with the same path, on different loaders" in {
//      val sys = ActorSystem("MultiLoader", TestHarness.defaultConfig)
//
//      try {
//        val jarA = getClass.getResource("/basic-extension.jar")
//        val jarB = getClass.getResource("/second-extension.jar")
//        val harnessClassLoaderA = new HarnessClassLoader(new URLClassLoader(Array(jarA)))
//        val harnessClassLoaderB = new HarnessClassLoader(new URLClassLoader(Array(jarB)))
//
//        val cm = sys.actorOf(Props[ComponentManager]())
//
//        println("Loading each component jar..")
//        val extB = Await.result(
//          (cm ? LoadComponent(
//            "SecondExtension",
//            "com.oracle.infy.wookiee.qa.SecondExtension",
//            Some(harnessClassLoaderB)
//          )).mapTo[Option[ActorRef]],
//          timeout.duration
//        )
//        val extA = Await.result(
//          (cm ? LoadComponent("BasicExtension", "com.oracle.infy.wookiee.qa.BasicExtension", Some(harnessClassLoaderA)))
//            .mapTo[Option[ActorRef]],
//          timeout.duration
//        )
//        Thread.sleep(1000L)
//        println("\nTrying to log from each..")
//        val instA = extA.map(act => Await.result((act ? "log").mapTo[String], timeout.duration)).getOrElse("")
//        val instB = extB.map(act => Await.result((act ? "log").mapTo[String], timeout.duration)).getOrElse("")
//
//        instA should not be instB
//      } finally {
//        sys.terminate()
//        ()
//      }
//    }
//  }
//
//  "The main harness class loader" should {
//    "Load up children classes from their respective loaders" in {
//      val sys = ActorSystem("MainMultiLoader", TestHarness.defaultConfig)
//
//      try {
//        println("Loading in resources..")
//        val jarA = getClass.getResource("/basic-extension.jar")
//        val jarOther = getClass.getResource("/other-extension.jar")
//        val harnessClassLoader = new HarnessClassLoader(new URLClassLoader(Array()))
//        val clA = HawkClassLoader("basic-extension", List(jarA), harnessClassLoader)
//        val clOther = HawkClassLoader("other-extension", List(jarOther), harnessClassLoader)
//
//        harnessClassLoader.addChildLoader(clA)
//        harnessClassLoader.addChildLoader(clOther)
//
//        val cm = sys.actorOf(Props[ComponentManager](), "test-comp-manager")
//
//        println("Loading each component jar..")
//        val extB = Await.result(
//          (cm ? LoadComponent("BasicExtension", "com.oracle.infy.wookiee.qa.BasicExtension", Some(harnessClassLoader)))
//            .mapTo[Option[ActorRef]],
//          timeout.duration
//        )
//        val extA = Await.result(
//          (cm ? LoadComponent("OtherExtension", "com.oracle.infy.wookiee.qa.OtherExtension", Some(harnessClassLoader)))
//            .mapTo[Option[ActorRef]],
//          timeout.duration
//        )
//        Thread.sleep(1000L)
//        println("\nTrying to log from each..")
//        val instA = extA.map(act => Await.result((act ? "log").mapTo[String], timeout.duration)).getOrElse("")
//        val instB = extB.map(act => Await.result((act ? "log").mapTo[String], timeout.duration)).getOrElse("")
//
//        instA should not be instB
//      } finally {
//        sys.terminate()
//        ()
//      }
//    }
//
//    "Should not load classes in when reading config" in {
//      val cf = HarnessActorSystem.renewConfigsAndClasses(Some(config))
//      cf.getString("other-extension.something.value") shouldEqual "changed"
//      HarnessActorSystem
//        .loader
//        .getChildLoaders
//        .exists(_.getURLs.exists(_.getPath.contains("other-extension"))) shouldEqual true
//    }
//
//    "Should isolate classes to their component's jars/dirs" in {
//      val otherStr = pollComponentReq[String]("other-extension", "log")
//      val basicStr = pollComponentReq[String]("basic-extension", "log")
//
//      otherStr shouldEqual "C"
//      basicStr shouldEqual "A"
//    }
//  }
//
//  "The extension reload framework" should {
//    "Reload an extension on command" in {
//      val cm = waitForSome({ testWookiee.componentManager })
//      val harnessClassLoader = new HarnessClassLoader(new URLClassLoader(Array()))
//      val cDir = compDir()
//
//      val result = Await.result(
//        (cm ? ReloadComponent(new File(s"$cDir/basic-extension.jar"), Some(harnessClassLoader))).mapTo[Boolean],
//        timeout.duration
//      )
//      result shouldBe true
//      harnessClassLoader.getChildLoaders.head.entityName shouldBe "basic-extension"
//    }
//
//    // Disabled because this doesn't pass in repeat runs, if you'd like to use
//    // it be sure to delete wookiee-test/src/test/resources/copy-extension.jar after each run
//    "Detect and load a new JAR" in {
//      import java.nio.file.Files
//      val cm = waitForSome({ testWookiee.componentManager })
//      val cDir = compDir()
//      val copy = new File(s"$cDir/copy-extension.jar")
//      Files.copy(new File(s"$cDir/basic-extension.jar").toPath, copy.toPath)
//      copy.deleteOnExit() // This often does nothing
//      Thread.sleep(2000L)
//      val comp = Await.result((cm ? GetComponent("copy-extension")).mapTo[Option[ActorRef]], timeout.duration)
//      comp.isDefined shouldBe true
//      val output = Await.result((comp.get ? "log").mapTo[String], timeout.duration)
//      output shouldBe "A"
//    }
//  }
//
//  override def beforeTestWookiee(): Unit = {
//    val cDir = compDir()
//    // Delete copy jar before tests, in case it's there
//    Try(new File(s"$cDir/copy-extension.jar").delete())
//    super.beforeTestWookiee()
//  }
//
//  override def startupWait: FiniteDuration = 40.seconds
//
//  override def config: Config = {
//    val cDir = compDir()
//
//    println(s"Component Directory: [$cDir]")
//    ConfigFactory.parseString(s"""{
//         | services.path = "src/"
//         | components {
//         |  path = "$cDir"
//         |  dynamic-loading = true
//         | }
//         |
//         | copy-extension {
//         |   enabled = true
//         |   manager = "com.oracle.infy.wookiee.qa.BasicExtension"
//         | }
//         |
//         | other-extension {
//         |  something {
//         |   value = "changed"
//         |  }
//         | }
//         |}""".stripMargin)
//  }
//
//  def waitForSome[T](isSome: => Option[T]): T = {
//    val waitTill = System.currentTimeMillis() + timeout.duration.toMillis
//    while (System.currentTimeMillis() < waitTill && isSome.isEmpty) Thread.sleep(500L)
//    isSome.get
//  }
//
//  def pollComponentReq[U](componentName: String, request: U): String = {
//    val cm = waitForSome({ testWookiee.componentManager })
//    val waitTill = System.currentTimeMillis() + timeout.duration.toMillis
//    while (System.currentTimeMillis() < waitTill) {
//      try {
//        val resp = Await.result(
//          (cm ? Request[U](componentName, ComponentRequest(request, Some(ComponentManager.ComponentRef))))
//            .mapTo[ComponentResponse[String]],
//          timeout.duration
//        )
//        return resp.resp
//      } catch {
//        case _: Throwable => // Ignore until we're out of time
//      }
//    }
//    "<timeout>"
//  }
//
//  def compDir(): String = {
//    val workingDir = new File(System.getProperty("user.dir")).listFiles().filter(_.isDirectory).map(_.getName)
//    if (workingDir.contains("wookiee-test")) "wookiee-test/src/test/resources"
//    else if (workingDir.contains("wookiee")) "wookiee/wookiee-test/src/test/resources"
//    else "src/test/resources"
//  }
//}
