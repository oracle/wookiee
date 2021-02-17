package com.oracle.infy.wookiee.health

import cats.effect.{IO, _}
import cats.implicits._
import com.oracle.infy.wookiee.health.json.Serde
import com.oracle.infy.wookiee.health.model.{Critical, Degraded, Health, Normal}
import com.oracle.infy.wookiee.utils.implicits._
import fs2.Stream
import com.oracle.infy.wookiee.http.WookieeHttpServer
import org.http4s.HttpRoutes
import org.http4s.dsl.io._

import scala.concurrent.ExecutionContext

object HeathCheckServer extends Serde {

  def of(
      healthF: () => IO[Health],
      host: String,
      port: Int,
      executionContext: ExecutionContext,
      additionalRoutes: Option[HttpRoutes[IO]]
  )(
      implicit timer: Timer[IO],
      cs: ContextShift[IO]
  ): Stream[IO, ExitCode] = {

    val routes = additionalRoutes match {
      case Some(r) => healthService(healthF) <+> r
      case None    => healthService(healthF)
    }

    WookieeHttpServer.of(host, port, routes, executionContext)
  }

  def healthService(healthF: () => IO[Health]): HttpRoutes[IO] = {
    HttpRoutes
      .of[IO] {
        case GET -> Root / "healthcheck" =>
          checkHealth(healthF()).flatMap(h => Ok(h))
        case GET -> Root / "healthcheck" / "lb" =>
          checkHealth(healthF()).flatMap(h => Ok(h.state))
        case GET -> Root / "healthcheck" / "nagios" =>
          checkHealth(healthF()).flatMap(h => Ok(s"${h.state}|${h.details}"))
      }
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
