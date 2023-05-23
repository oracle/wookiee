package com.oracle.infy.wookiee.component.helidon.web.http

import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter
import io.helidon.webserver.{ServerRequest, ServerResponse}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.function.BiConsumer

class WookieeRouterSpec extends AnyWordSpec with Matchers {
  "WookieeRouter" should {
    def makeHandler: WookieeRouter.Handler = new BiConsumer[ServerRequest, ServerResponse]() {
      override def accept(t: ServerRequest, u: ServerResponse): Unit = ()
    }

    "correctly add a route and find the handler" in {
      val router = new WookieeRouter
      val handler = makeHandler
      val handler2 = makeHandler
      val handler3 = makeHandler

      router.addRoute("/api/v1/endpoint", "GET", handler)
      router.addRoute("/api", "GET", handler2)
      router.addRoute("/$api", "GET", handler3)
      router.findHandler("/api/v1/endpoint", "GET") mustBe Some(handler)
      router.findHandler("/api", "GET") mustBe Some(handler2)
      router.findHandler("/something", "GET") mustBe Some(handler3)
    }

    "fail on empty path" in {
      intercept[IllegalArgumentException] {
        val router = new WookieeRouter
        val handler = makeHandler

        router.addRoute("", "GET", handler)
      }
    }

    "return none on failed find" in {
      val router = new WookieeRouter

      router.addRoute("/api/ending", "GET", makeHandler)
      router.findHandler("/api", "GET") mustBe None
    }

    "get a route with a wildcard" in {
      val router = new WookieeRouter
      val handler = makeHandler
      val handler2 = makeHandler
      val handler3 = makeHandler
      val handler4 = makeHandler

      router.addRoute("/api/$version/endpoint", "GET", handler)
      router.addRoute("/api/version/endpoint", "GET", handler2)
      router.addRoute("/api/$version/endpoint/$value", "GET", handler3)
      router.addRoute("/api/version/endpoint/value", "GET", handler4)
      router.findHandler("/api/v1/endpoint", "GET") mustBe Some(handler)
      router.findHandler("/api/version/endpoint", "GET") mustBe Some(handler2)
      router.findHandler("/api/v1/endpoint/something", "GET") mustBe Some(handler3)
      router.findHandler("/api/version/endpoint/value", "GET") mustBe Some(handler4)
    }

    "return None when trying to find a handler for a path that does not exist" in {
      val router = new WookieeRouter
      val handler = makeHandler

      router.addRoute("/api/v1/endpoint", "GET", handler)
      router.findHandler("/api/v2/endpoint", "GET") mustBe None
    }

    "return None when trying to find a handler for a method that does not exist" in {
      val router = new WookieeRouter
      val handler = makeHandler

      router.addRoute("/api/v1/endpoint", "GET", handler)
      router.findHandler("/api/v1/endpoint", "POST") mustBe None
    }
  }
}
