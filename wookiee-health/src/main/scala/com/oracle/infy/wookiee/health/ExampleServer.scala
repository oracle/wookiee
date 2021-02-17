package com.oracle.infy.wookiee.health

import cats.effect.{ExitCode, IO, IOApp}
import com.oracle.infy.wookiee.health.model._
import org.http4s.HttpRoutes
import org.http4s.dsl.io._

import scala.concurrent.ExecutionContext

object ExampleServer extends IOApp {

  implicit val ec = ExecutionContext.global

  def run(args: List[String]): IO[ExitCode] = {
    HeathCheckServer
      .of(
        () =>
          IO(
            Health(
              Normal,
              s"Thunderbirds are GO",
              Map("ZK" -> Health(Critical, "No host found for ZK"), "Kafka" -> Health(Degraded, "Kafka not running"))
            )
          ),
        "localhost",
        8081,
        ec,
        Some(additionalRoutes)
      )
      .compile
      .drain
      .as(ExitCode.Success)
  }

  def additionalRoutes: HttpRoutes[IO] = {
    HttpRoutes
      .of[IO] {
        case GET -> Root / "ping" => Ok("Pong")
      }
  }

}
