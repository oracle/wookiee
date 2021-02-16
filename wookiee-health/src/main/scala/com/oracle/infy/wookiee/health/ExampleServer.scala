package com.oracle.infy.wookiee.health

import cats.effect.{ExitCode, IO, IOApp}
import com.oracle.infy.wookiee.health.model._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

object ExampleServer extends IOApp {

  implicit val ec = ExecutionContext.global

  def run(args: List[String]): IO[ExitCode] = {
    implicit val logger: Logger[IO] = Slf4jLogger.create[IO].unsafeRunSync()
    for {
      server <- HeathCheckServer.of(
        () =>
          IO(
            Health(
              Normal,
              s"Thunderbirds are GO",
              Map("ZK" -> Health(Critical, "No host found for ZK"), "Kafka" -> Health(Degraded, "Kafka not running"))
            )
          ),
        "localhost",
        8081
      )
      exitStatus <- server.compile.drain.as(ExitCode.Success)
    } yield exitStatus
  }

}
