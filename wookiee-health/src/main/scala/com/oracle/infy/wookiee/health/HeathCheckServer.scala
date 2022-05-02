package com.oracle.infy.wookiee.health

import cats.effect.{IO, _}
import cats.implicits._
import com.oracle.infy.wookiee.health.json.Serde
import com.oracle.infy.wookiee.health.model.{Critical, Degraded, Normal, WookieeHealth}
import com.oracle.infy.wookiee.grpc.utils.implicits._
import fs2.Stream
import com.oracle.infy.wookiee.http.WookieeHttpServer
import org.http4s.HttpRoutes
import org.http4s.dsl.io._

import scala.concurrent.ExecutionContext
import cats.effect.Temporal

object HeathCheckServer extends Serde {

  def of(
      host: String,
      port: Int,
      healthF: () => IO[WookieeHealth],
      executionContext: ExecutionContext,
      additionalRoutes: Option[HttpRoutes[IO]]
  )(implicit timer: Temporal[IO]): Stream[IO, ExitCode] = {

    val routes = additionalRoutes match {
      case Some(r) => healthCheckRoutes(healthF) <+> r
      case None    => healthCheckRoutes(healthF)
    }

    WookieeHttpServer.of(host, port, routes, executionContext)
  }

  def healthCheckRoutes(healthF: () => IO[WookieeHealth]): HttpRoutes[IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root / "healthcheck" =>
          checkHealth(healthF()).flatMap(h => Ok(h))
        case GET -> Root / "healthcheck" / "lb" =>
          checkHealth(healthF()).flatMap(h => Ok(h.state))
        case GET -> Root / "healthcheck" / "nagios" =>
          checkHealth(healthF()).flatMap(h => Ok(s"${h.state}|${h.details}"))
      }

  private def checkHealth(health: IO[WookieeHealth]): IO[WookieeHealth] =
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
