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

  def getParameterMap = {
    val paramMap = new mutable.LinkedHashMap[String, (AnyRef, Boolean)]()
    this map { kv =>
      kv._2 match {
        case JObject(j) =>
          j foreach { obj =>
            // Query params take precedence over payload
            if (!paramMap.contains(obj.name)) {
              paramMap(obj.name) = (obj.value match {
                case JString(js) => js
                case JInt(js) => js
                case JBool(js) => js.toString
                case JDouble(js) => js.toString
                case _ => obj.value
              }, false)
            }
          }
        case _ => if (kv._1 != SockoCommandBean.Content) paramMap(kv._1) = (kv._2, true)
      }
    }
    paramMap
  }

  def getBodyParams = {
    val paramMap = new mutable.LinkedHashMap[String, Any]()
    if (contains(CommandBean.KeyEntity)) {
      val jObj: JObject = get(CommandBean.KeyEntity).get.asInstanceOf[JObject]
      jObj.values foreach { obj =>
        paramMap(obj._1) = obj._2
      }
    }
    paramMap
  }

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
