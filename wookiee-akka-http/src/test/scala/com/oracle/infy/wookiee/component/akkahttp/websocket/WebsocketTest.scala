package com.oracle.infy.wookiee.component.akkahttp.websocket

import akka.http.WSWrapper
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.WSProbe
import akka.stream.Supervision.{Resume, Stop}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.oracle.infy.wookiee.component.akkahttp.routes.AkkaHttpEndpointRegistration.ErrorHolder
import com.oracle.infy.wookiee.component.akkahttp.routes.{
  AkkaHttpEndpointRegistration,
  AkkaHttpRequest,
  EndpointOptions,
  EndpointType,
  ExternalAkkaHttpRouteContainer
}
import com.typesafe.config.ConfigFactory
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

class WebsocketTest extends WSWrapper {
  case class AuthHolder(value: String)
  case class ParamHolder(p1: String, q1: String)
  case class Input(value: String)
  case class Output(value: String)

  implicit val formats: DefaultFormats.type = DefaultFormats

  var errorThrown: Option[Throwable] = None

  val toOutput: (Input, WebsocketInterface[Input, Output, _]) => Unit = {
    (input: Input, inter: WebsocketInterface[Input, Output, _]) =>
      println(s"got input $input")
      inter.reply(Output(input.value + "-output"))
  }

  val toText: Output => TextMessage.Strict = { resp: Output =>
    TextMessage(resp.value)
  }

  "Websockets" should {
    implicit val ec: ExecutionContext = system.dispatcher

    def routes: Route = ExternalAkkaHttpRouteContainer.getRoutes.reduceLeft(_ ~ _)
    var closed: Boolean = false
    var lastHit: Option[Input] = None

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "basic",
      EndpointType.EXTERNAL, { _ =>
        Future.successful(AuthHolder("none"))
      }, { (_, msg: TextMessage) =>
        msg.toStrict(5.seconds).map(s => Input(s.getStrictText))
      },
      toOutput,
      toText, { (_: AuthHolder, lh: Option[Input]) =>
        println("Called onClose")
        closed = true
        lastHit = lh
      }
    )

    "basic websocket support" in {
      val wsClient = WSProbe()

      WS("/basic", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("abcdef-output")

          wsClient.sendMessage("abcdef2")
          wsClient.expectMessage("abcdef2-output")

          wsClient.sendCompletion()
          wsClient.expectCompletion()

          Thread.sleep(500L)
          closed mustEqual true
          lastHit.get mustEqual Input("abcdef2")
        }
    }

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, ParamHolder](
      "param/$p1",
      EndpointType.EXTERNAL, { req =>
        Future.successful(ParamHolder(req.segments.head, req.queryParams("q1")))
      }, { (params: ParamHolder, msg: TextMessage) =>
        msg.toStrict(5.seconds).map(s => Input(s"${params.p1}-${params.q1}-${s.getStrictText}"))
      },
      toOutput,
      toText, { (_: ParamHolder, _: Option[Input]) =>
        println("Called onClose")
      }
    )

    "query parameter and segment support" in {
      val wsClient = WSProbe()

      WS("/param/pValue?q1=qValue", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("pValue-qValue-abcdef-output")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, ParamHolder](
      "error/auth",
      EndpointType.EXTERNAL, { _ =>
        Future.failed(new IllegalAccessError("Not allowed buster!"))
      }, { (_, msg: TextMessage) =>
        msg.toStrict(5.seconds).map(s => Input(s.getStrictText))
      },
      toOutput,
      toText, { (_: ParamHolder, _: Option[Input]) =>
        println("Called onClose")
      }, { _: AkkaHttpRequest =>
        {
          case err: IllegalAccessError =>
            println("Got ERROR:")
            err.printStackTrace()
            errorThrown = Some(err)
            complete(write(ErrorHolder(err.getMessage)))
        }
      }
    )

    "handles auth errors cleanly" in {
      val wsClient = WSProbe()

      val route = WS("/error/auth", wsClient.flow) ~> routes

      val resp = Await.result(route.entity.toStrict(5.seconds).map(_.getData().utf8String), 5.seconds)
      resp mustEqual """{"error":"Not allowed buster!"}"""
    }

    var resumeHit = false
    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "error/ws",
      EndpointType.EXTERNAL, { _ =>
        Future.successful(AuthHolder("none"))
      }, { (_, tm: TextMessage) =>
        println(s"Text: ${tm.getStrictText}")
        if (tm.getStrictText == "recover") throw new IllegalStateException("Will recover")
        else if (tm.getStrictText == "stop") throw new RuntimeException("Will stop the stream")
        else tm.toStrict(5.seconds).map(s => Input(s.getStrictText))
      },
      toOutput,
      toText, { (_: AuthHolder, _: Option[Input]) =>
        println("Called onClose")
        closed = true
      },
      wsErrorHandler = {
        case _: IllegalStateException =>
          println("Resume error hit")
          resumeHit = true
          Resume // Will skip the errant event
        case _: RuntimeException =>
          println("Stop error hit")
          Stop // Will close this stream
      }
    )

    "recover from errors during ws processing" in {
      val wsClient = WSProbe()
      closed = false

      WS("/error/ws", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("recover")
          wsClient.inProbe.ensureSubscription()

          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("abcdef-output")
          resumeHit mustEqual true

          wsClient.sendMessage("stop")
          wsClient.expectCompletion()
          closed mustEqual true
        }
    }

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "multi",
      EndpointType.EXTERNAL, { _ =>
        Future.successful(AuthHolder("none"))
      }, { (_, msg: TextMessage) =>
        msg.toStrict(5.seconds).map(s => Input(s.getStrictText))
      }, { (input: Input, inter: WebsocketInterface[Input, Output, _]) =>
        println(s"got multi input $input")
        inter.reply(Output(input.value + "-output1"))
        inter.reply(Output(input.value + "-output2"))
        inter.reply(Output(input.value + "-output3"))
      },
      toText, { (_: AuthHolder, _: Option[Input]) =>
        println("Called onClose")
        closed = true
      }
    )

    "can send back more than one reply for a single input" in {
      val wsClient = WSProbe()

      WS("/multi", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("abcdef-output1")
          wsClient.expectMessage("abcdef-output2")
          wsClient.expectMessage("abcdef-output3")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    "can send multiple inputs in a row" in {
      val wsClient = WSProbe()

      WS("/multi", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("first")
          wsClient.sendMessage("second")
          val outputs = 1
            .to(6)
            .map { _ =>
              wsClient.expectMessage().asTextMessage.getStrictText
            }
            .toList
          val exp = List(
            "first-output1",
            "first-output2",
            "first-output3",
            "second-output1",
            "second-output2",
            "second-output3"
          )

          outputs.intersect(exp).size mustEqual 6

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    val events = 100000
    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "perf",
      EndpointType.EXTERNAL, { _ =>
        Future.successful(AuthHolder("none"))
      }, { (_, msg: TextMessage) =>
        msg.toStrict(5.seconds).map(s => Input(s.getStrictText))
      }, { (input: Input, inter: WebsocketInterface[Input, Output, _]) =>
        println(s"got perf input '${input.value}', sending [$events] events")
        1.to(events).foreach { i =>
          inter.reply(Output(input.value + s"-output$i"))
        }
      },
      toText, { (_: AuthHolder, _: Option[Input]) =>
        println("Called onClose")
        closed = true
      }
    )

    "can send high volume and get all events" in {
      val wsClient = WSProbe()

      WS("/perf", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("begin")
          val expect = 1.to(events).map(i => s"begin-output$i").toList
          val result = ListBuffer[String]()
          try {
            1.to(events).foreach { _ =>
              result += wsClient.expectMessage().asTextMessage.getStrictText
            }
          } catch {
            case _: AssertionError =>
          }

          expect.intersect(result).size mustEqual expect.size

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "cors",
      EndpointType.EXTERNAL, { _ =>
        Future.successful(AuthHolder("none"))
      }, { (_, msg: TextMessage) =>
        msg.toStrict(5.seconds).map(s => Input(s.getStrictText))
      },
      toOutput,
      toText, { (_: AuthHolder, lh: Option[Input]) =>
        println("Called onClose")
        closed = true
        lastHit = lh
      },
      options = EndpointOptions
        .default
        .copy(
          corsSettings =
            Some(CorsSettings(ConfigFactory.parseString("""akka-http-cors {
              |allowed-origins = "*"
              |allow-generic-http-requests = true
              |allow-credentials = false
              |allowed-headers = []
              |allowed-methods = ["OPTIONS","CONNECT","GET","HEAD","PATCH"]
              |exposed-headers = []
              |}""".stripMargin)))
        )
    )

    "websocket cors support" in {
      val wsClient = WSProbe()

      WS("/cors", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("abcdef-output")

          wsClient.sendMessage("abcdef2")
          wsClient.expectMessage("abcdef2-output")

          wsClient.sendCompletion()
          wsClient.expectCompletion()

          Thread.sleep(500L)
          closed mustEqual true
          lastHit.get mustEqual Input("abcdef2")
        }
    }

    val maxEventsToTry = 100
    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "stop",
      EndpointType.EXTERNAL, { _ =>
        Future.successful(AuthHolder("none"))
      }, { (_, msg: TextMessage) =>
        msg.toStrict(5.seconds).map(s => Input(s.getStrictText))
      }, { (input: Input, inter: WebsocketInterface[Input, Output, _]) =>
        println(s"got perf input '${input.value}', sending [$events] events")
        1.to(maxEventsToTry).foreach { i =>
          inter.reply(Output(input.value + s"-output$i"))
          Thread.sleep(50L)
          if (i == maxEventsToTry / 2)
            inter.stop()
        }
      },
      toText, { (_: AuthHolder, _: Option[Input]) =>
        println("Called onClose")
        closed = true
      }
    )

    "can be stopped mid stream successfully" in {
      val wsClient = WSProbe()

      WS("/stop", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("begin")
          var gotMessages = 0
          while (Try(wsClient.expectMessage()).isSuccess) {
            gotMessages += 1
          }

          gotMessages == maxEventsToTry / 2 mustEqual true
        }
    }
  }

  override def testConfigSource: String =
    """
      |akka.test.single-expect-default = 60000
      |""".stripMargin
}
