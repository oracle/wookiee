package com.oracle.infy.wookiee.component.helidon

import com.oracle.infy.wookiee.component.helidon.web.client.WookieeWebClient
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointType
import com.oracle.infy.wookiee.test.TestHarness.getFreePort
import com.typesafe.config.ConfigFactory
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HelidonManagerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  implicit val formats: Formats = DefaultFormats
  private var manager: HelidonManager = _

  private val internalPort = getFreePort
  private val externalPort = getFreePort

  override protected def beforeAll(): Unit = {
    val conf = ConfigFactory.parseString(s"""
                                            |instance-id = "helidon-test-$internalPort"
                                            |wookiee-helidon {
                                            |  web {
                                            |    internal-port = $internalPort
                                            |    external-port = $externalPort
                                            |  }
                                            |}
                                            |""".stripMargin)

    manager = new HelidonManager("wookiee-helidon", conf)
    manager.start()

    manager.registerEndpoint(
      "/api/$variable/endpoint",
      EndpointType.INTERNAL,
      "POST",
      (req, res) => {
        req
          .content()
          .as(classOf[String])
          .thenAccept(jsonString => {
            val json = parse(jsonString)
            res.headers().add("content-type", "application/json")
            res.send(compact(render(json)))
            ()
          })
        ()
      }
    )
  }

  override protected def afterAll(): Unit =
    manager.prepareForShutdown()

  "Helidon Manager" should {
    "handle a call to the '/api/test/endpoint' endpoint" in {
      val jsonPayload = """{"key":"value"}"""
      val responseContent = WookieeWebClient.oneOff(
        s"http://localhost:$internalPort",
        "/api/test/endpoint",
        "POST",
        jsonPayload,
        Map()
      )

      // Check the response content
      responseContent mustBe jsonPayload
    }
  }
}
