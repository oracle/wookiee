package com.oracle.infy.wookiee.http

import cats.effect.{ExitCode, IO}
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._

import scala.concurrent.ExecutionContext

object WookieeHttpServer {

  def of(
      host: String,
      port: Int,
      httpRoutes: HttpRoutes[IO],
      executionContext: ExecutionContext
  ): Stream[IO, ExitCode] = {
    val httpApp = httpRoutes.orNotFound
    BlazeServerBuilder[IO]
      .withExecutionContext(executionContext)
      .bindHttp(port, host)
      .withoutBanner
      .withHttpApp(httpApp)
      .serve
  }
}
