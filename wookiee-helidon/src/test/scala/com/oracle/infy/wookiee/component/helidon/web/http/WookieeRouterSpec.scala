package com.oracle.infy.wookiee.component.helidon.web.http

import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter
import io.helidon.webserver.{Handler, ServerRequest, ServerResponse}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WookieeRouterSpec extends AnyWordSpec with Matchers {
  "WookieeRouter" should {
    def makeHandler: Handler = (_: ServerRequest, _: ServerResponse) => ()

    "correctly add a route and find the handler" in {
      val router = new WookieeRouter
      val handler = makeHandler
      val handler2 = makeHandler
      val handler3 = makeHandler

      router.addRoute("api/v1/endpoint", "GET", handler)
      router.findHandler("/api/v1/endpoint", "GET") mustBe Some(handler)

      router.addRoute("/api", "GET", handler2)
      router.findHandler("/api", "GET") mustBe Some(handler2)

      router.addRoute("/$api", "GET", handler3)
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

    "perform unapply on its relevant classes" in {
      val rt = WookieeRouter.ServiceHolder(null, new WookieeRouter)
      WookieeRouter.ServiceHolder.unapply(rt) must not be None
    }

    "doesn't get too greedy algo-wise" in {
      val router = new WookieeRouter
      val handler = makeHandler
      val handler2 = makeHandler

      router.addRoute("/api/version/incorrect", "GET", handler)
      router.addRoute("/api/$version/endpoint", "GET", handler2)

      router.findHandler("/api/version/endpoint", "GET") mustBe Some(handler2)
    }

    "doesn't match when it shouldn't" in {
      val router = new WookieeRouter
      val handler = makeHandler

      router.addRoute("/api/$version", "GET", handler)
      router.findHandler("/api/version/endpoint", "GET") mustBe None
      router.findHandler("/api", "GET") mustBe None
    }

    "handle tons of registrations and finds" in {
      // Last run: Added 100,000 routes in ~1s, Found 1,000,000 routes in ~2s
      val N = 100000
      val router = new WookieeRouter
      val handler = makeHandler

      def doNTimes(fn: (Int, Int) => Unit): Unit = {
        val iters = Math.sqrt(N).toInt
        1.to(iters).foreach(i => 1.to(iters).foreach(j => fn(i, j)))
      }

      val startTime = System.currentTimeMillis()
      doNTimes({ (i, j) =>
        router.addRoute(s"/api/a$i/b$j", "GET", handler)
      })
      println(s"Added $N routes in ${System.currentTimeMillis() - startTime}ms")

      val startTime2 = System.currentTimeMillis()
      1.to(10)
        .foreach(
          _ =>
            doNTimes({ (i, j) =>
              router.findHandler(s"/api/a$i/b$j", "GET") must not be None
              ()
            })
        )
      println(s"Found ${N * 10} routes in ${System.currentTimeMillis() - startTime2}ms")
    }
  }
}
