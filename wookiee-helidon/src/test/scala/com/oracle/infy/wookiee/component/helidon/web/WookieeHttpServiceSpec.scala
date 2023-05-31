package com.oracle.infy.wookiee.component.helidon.web

import com.oracle.infy.wookiee.service.WookieeService
import com.oracle.infy.wookiee.test.BaseWookieeTest
import com.typesafe.config.Config
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicReference

class TestHttpService(config: Config) extends WookieeHttpService(config) {

  override def addCommands(): Unit =
    WookieeHttpServiceSpec.calledAddCommands.set(true)
}

object WookieeHttpServiceSpec {
  val calledAddCommands: AtomicReference[Boolean] = new AtomicReference(false)
}

class WookieeHttpServiceSpec extends AnyWordSpec with Matchers with BaseWookieeTest {

  "Wookiee HTTP Service trait" should {
    "kick off addCommands when wookiee-helidon is ready" in {
      WookieeHttpServiceSpec.calledAddCommands.get() mustEqual true
    }
  }

  override def servicesMap: Option[Map[String, Class[_ <: WookieeService]]] =
    Some(Map("TestHttpService" -> classOf[TestHttpService]))
}
