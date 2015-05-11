package com.webtrends.harness.component.socko.utils

import java.net.InetSocketAddress
import com.webtrends.harness.command.CommandBean
import net.liftweb.json.JsonAST._
import org.mashupbots.socko.events.HttpRequestEvent

import scala.collection.mutable
import scala.util.Try

class SockoCommandBean(rawEvent: HttpRequestEvent) extends CommandBean {
  val headers = rawEvent.nettyHttpRequest.headers
  if (!headers.contains("Remote-Address")) {
    Try { headers.add("Remote-Address", rawEvent.context.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress.getHostAddress) }
  }
  val query = rawEvent.endPoint.queryString
  val event = rawEvent

  def getContent: Option[Array[Byte]] = {
    get(SockoCommandBean.Content).asInstanceOf[Option[Array[Byte]]]
  }
}

object SockoCommandBean {
  def apply(params:Map[String, AnyRef], event: HttpRequestEvent) = {
    val bean = new SockoCommandBean(event)
    params foreach {
      bean += _
    }
    bean
  }

  val Content = "raw-content"
}
