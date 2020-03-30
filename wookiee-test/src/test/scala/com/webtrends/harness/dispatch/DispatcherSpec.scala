package com.webtrends.harness.dispatch

import com.webtrends.harness.command.WeatherCommand
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.Future

class DispatcherSpec extends WordSpecLike with Matchers {
  var registeredParam = ""

  case class TestConfig(param: String) extends DispatcherConfig[String, String] {
    override def unmarshallInput(any: Any): String = {
      any.toString + param
    }

    override def marshallOutput(output: String): Array[Byte] = {
      (output + param).getBytes
    }

    override def register(name: String): Future[Boolean] = {
      registeredParam = name
      Future.successful(true)
    }
  }

  "Dispatchers" should {
    "Register endpoints" in {
      val endpoint = Endpoint(TestConfig("-test"), classOf[WeatherCommand])
      registeredParam must equal "TestEndpoint"
    }
  }
}
