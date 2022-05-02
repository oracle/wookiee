package com.oracle.infy.wookiee.http

import cats.effect.{ExitCode, IO}
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.implicits._
import org.http4s.server.blaze._

import scala.concurrent.ExecutionContext
import cats.effect.Temporal

object WookieeHttpServer {

  def of(host: String, port: Int, httpRoutes: HttpRoutes[IO], executionContext: ExecutionContext)(
      implicit timer: Temporal[IO]): Stream[IO, ExitCode] = {
    val httpApp = httpRoutes.orNotFound
    BlazeServerBuilder[IO](executionContext)
      .bindHttp(port, host)
      .withoutBanner
      .withHttpApp(httpApp)
      .serve
  }
}
