package com.oracle.infy.wookiee.health

import cats.data.Kleisli
import cats.effect.{IO, _}
import com.oracle.infy.wookiee.health.json.Serde
import com.oracle.infy.wookiee.health.model.{Critical, Degraded, Health, Normal}
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.{HttpRoutes, Request, Response}
import com.oracle.infy.wookiee.utils.implicits._

import scala.concurrent.ExecutionContext

object HeathCheckServer extends Serde {

  def of(healthF: () => IO[Health], host: String, port: Int)(
      implicit executionContext: ExecutionContext,
      timer: Timer[IO],
      cs: ContextShift[IO],
      logger: Logger[IO]
  ): IO[Stream[IO, ExitCode]] = {
    for {
      _ <- IO(logger.info("Health check server started"))
      server <- IO(
        BlazeServerBuilder[IO](executionContext)
          .bindHttp(port = port, host = host)
          .withHttpApp(healthService(healthF))
          .serve
      )
    } yield server
  }

  def healthService(healthF: () => IO[Health]): Kleisli[IO, Request[IO], Response[IO]] = {
    HttpRoutes
      .of[IO] {
        case GET -> Root / "healthcheck" =>
          checkHealth(healthF()).flatMap(h => Ok(h))
        case GET -> Root / "healthcheck" / "lb" =>
          checkHealth(healthF()).flatMap(h => Ok(h.state))
        case GET -> Root / "healthcheck" / "nagios" =>
          checkHealth(healthF()).flatMap(h => Ok(s"${h.state}|${h.details}"))
      }
      .orNotFound
  }

  private def checkHealth(health: IO[Health]): IO[Health] =
    for {
      h <- health
      components = Seq(h) ++ h.components.values
      allStates = components.map(_.state)

      overallState = if (allStates.contains(Degraded)) Degraded
      else if (allStates.contains(Critical)) Critical
      else Normal

      overallDetails = if (overallState === Normal) h.details
      else components.filterNot(_.state === Normal).map(_.details).mkString(";")

    } yield h.copy(state = overallState, details = overallDetails)

}
