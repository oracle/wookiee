package com.oracle.infy.wookiee.component.web.http

import com.oracle.infy.wookiee.component.web.client.WookieeWebClient
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.web.http.HttpObjects.{EndpointType, WookieeRequest}
import com.oracle.infy.wookiee.component.web.http.impl.WookieeRouter
import com.oracle.infy.wookiee.component.web.http.impl.WookieeRouter.{EndpointMeta, HttpHandler}
import com.oracle.infy.wookiee.component.web.ws.WookieeWebsocket
import io.helidon.webserver.{ServerRequest, ServerResponse}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import javax.websocket.Session

class WookieeRouterSpec extends AnyWordSpec with Matchers {
  "WookieeRouter" should {
    def makeHandler: HttpHandler = HttpHandler((_: ServerRequest, _: ServerResponse) => ())

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

    "allow one to get all registered routes" in {
      val router = new WookieeRouter
      val handler = makeHandler
      val handler2 = makeHandler
      val handler3 = makeHandler
      val handler4 = makeHandler

      router.addRoute("/api/$version/endpoint/$value", "GET", handler3)
      router.addRoute("/api/version/endpoint/value", "GET", handler4)
      router.addRoute("/api/v1/endpoint", "GET", handler)
      router.addRoute("/api/v1/endpoint", "POST", handler)
      router.addRoute("/api/version/endpoint", "GET", handler2)
      router.addRoute("/api/v1/endpoint/$something", "OPTIONS", handler3)
      router.addRoute("/api/v1/endpoint/$something", "TRACE", handler3)
      router.addRoute("/api/$version/endpoint/value", "PUT", handler4)

      val endpoints = router.listOfRoutes()
      endpoints.size mustBe 8
      endpoints.contains(EndpointMeta("GET", "/api/*/endpoint/*"))
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

      val rt2 = WookieeRouter.WebsocketHandler(new WookieeWebsocket[Any] {
        override def path: String = "/"
        override def endpointType: EndpointType = EndpointType.BOTH
        override def handleText(text: String, request: HttpObjects.WookieeRequest, authInfo: Option[Any])(
            implicit session: Session
        ): Unit =
          ???
      })
      WookieeRouter.WebsocketHandler.unapply(rt2) must not be None
    }

    "doesn't get too greedy algo-wise" in {
      val router = new WookieeRouter
      val handler = makeHandler
      val handler2 = makeHandler

      router.addRoute("/api/version/incorrect", "GET", handler)
      router.addRoute("/api/$version/endpoint", "GET", handler2)

      router.findHandler("/api/version/endpoint", "GET") mustBe Some(handler2)
    }

    "query string parser should handle odd cases" in {
      val params = WookieeWebClient.getQueryParams("a=b=c&d=e&f=g")
      params.size mustBe 2
    }

    "doesn't match when it shouldn't" in {
      val router = new WookieeRouter
      val handler = makeHandler

      router.addRoute("/api/$version", "GET", handler)
      router.findHandler("/api/version/endpoint", "GET") mustBe None
      router.findHandler("/api", "GET") mustBe None
    }

    "have request that acts as a bean" in {
      val bean = WookieeRequest()
      bean("test") = "test"
      bean("test") mustEqual "test"
      bean.getValue[String]("test").get mustEqual "test"
      bean.getValue[String]("test2").isDefined mustEqual false
      // wrong type
      val req: Option[WookieeRequest] = bean.getValue[WookieeRequest]("test")
      req.isDefined mustEqual false
      bean.addValue("key", "value")
      bean("key") mustEqual "value"
      bean.appendMap(Map("key2" -> "value2"))
      bean("key2") mustEqual "value2"
    }

    "handle tons of registrations and finds" in {
      // Last fastest run: Added 100,000 routes in ~150ms, Found 1,000,000 routes in ~500ms
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
