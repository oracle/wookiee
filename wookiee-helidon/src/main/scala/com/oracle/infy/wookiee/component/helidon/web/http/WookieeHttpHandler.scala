package com.oracle.infy.wookiee.component.helidon.web.http

import com.oracle.infy.wookiee.command.WookieeCommand
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.{WookieeRequest, WookieeResponse}

import scala.concurrent.Future

trait WookieeHttpHandler extends WookieeCommand[WookieeRequest, WookieeResponse] {
  def method: String

  override def execute(input: WookieeRequest): Future[WookieeResponse]
}
