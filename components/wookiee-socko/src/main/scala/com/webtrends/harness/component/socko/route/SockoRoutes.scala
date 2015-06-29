/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.component.socko.route

import java.io._
import java.nio.charset.Charset
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import com.webtrends.harness.command.{Command, CommandBean, CommandException, CommandResponse}
import com.webtrends.harness.component.socko.AddSockoHandler
import com.webtrends.harness.component.socko.utils.{SockoCommandBean, SockoCommandException, SockoUtils}
import com.webtrends.harness.component.{ComponentHelper, ComponentManager, ComponentMessage}
import io.netty.handler.codec.http.QueryStringDecoder
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.json.JsonParser.ParseException
import net.liftweb.json._
import net.liftweb.json.ext.JodaTimeSerializers
import org.mashupbots.socko.events.{HttpRequestEvent, HttpResponseStatus, ImmutableHttpHeaders}
import org.mashupbots.socko.infrastructure.CharsetUtil
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.io.Source
import scala.util.{Failure, Success}

class AutoSockoHandler(matcher:(HttpRequestEvent) => Option[mutable.HashMap[String, AnyRef]], handler:(HttpRequestEvent, mutable.HashMap[String, AnyRef]) => Unit) extends SockoRouteHandler {
  /**
   * Attempts to handle the Socko route
   *
   * @param bean The http request
   * @return true if route was handled, false otherwise
   */
  override def handleSockoRoute(event:HttpRequestEvent, bean:mutable.HashMap[String, AnyRef]) = handler(event, bean)

  /**
   * Checks if this handler matches the route in the event, returned map will be used to handle request
   * or pass None back if this is not the right path
   *
   * @param event Event receive by Socko
   * @return None- path does not match, Some(map)- path matches, extract variables
   */
  override def matchSockoRoute(event: HttpRequestEvent): Option[mutable.HashMap[String, AnyRef]] = matcher(event)
}

/**
 * Wrapper case class to make Socko headers easier to deal with
 *
 * @param headerName the name of the header
 * @param headerValue the value of the header
 */
case class SockoHeader(headerName:String, headerValue:String)

/**
 * SockoRoutes enable developers to simply add traits to their commands and gain instant http capabilities
 * For SockoRoutes a handler actor is created for each Command and each trait that is applied to the command.
 * The socko handlers are registered in the Socko Manager.
 */
private[route] trait SockoRoutes extends ComponentHelper {
  this:Command =>

  implicit val formats = Serialization.formats(NoTypeHints) ++ JodaTimeSerializers.all

  import context.dispatcher

  def applyQSMap(bean:SockoCommandBean) {
    // this fixedMap basically takes the queryStringMap and removes the List of elements if there is
    // only a single element in the list.
    val fixedMap = bean.event.endPoint.queryStringMap map { e => e._1 -> e._2.last }
    bean.appendMap(fixedMap)
  }

  def marshallObject(obj: Any, respType: String = "json"): Array[Byte] = {
    val realRespType = SockoUtils.normalizeContentType(respType)
    if (realRespType == "application/json") {
      obj match {
        case s: String => s.getBytes
        case _ => compactRender(decompose(obj)).getBytes
      }
    } else obj match {
      case bytes: Array[Byte] =>
        bytes
      case _ =>
        obj.toString.getBytes
    }
  }

  /**
   * Sets the response code for the command using the trait
   * So if you are creating an item and wanted to send back the CREATED status
   * then you just override this in your command
   *
   * @return
   */
  def responseStatusCode = HttpResponseStatus.OK

  /**
   * Function that allows you to override the headers for a response. The Map allows you to specifically
   * set headers for specific HTTPMethods, if None set for HttpMethod, then the header will apply to all
   * methods. This is done so that if you have multiple traits like SprayPut, SprayPost, SprayOptions, SprayGet, etc.
   * you can apply different headers depending on what you are doing.
   *
   * @return
   */
  def getResponseHeaders(headers: ImmutableHttpHeaders) : Map[String, List[SockoHeader]] = {
    Map[String, List[SockoHeader]]().empty
  }

  def defaultPathMatcher(event: HttpRequestEvent, paths : Map[String, String]): Option[mutable.HashMap[String, AnyRef]] = {
    paths foreach { p =>
      Command.matchPath(p._2, event.endPoint.path) match {
        case Some(b) =>
          b.addValue(CommandBean.KeyPath, p._1)
          val bean = new SockoCommandBean(event)
          bean.appendMap(b.toMap)
          return Some(bean)
        case None => // ignore and continue in the stream
      }
    }
    None
  }

  protected def getRejectionHandler(event:HttpRequestEvent, e:Throwable) = {

    def writeErrorResponse(status: HttpResponseStatus, body: String = ""): Unit = {
      val encBody = body.getBytes("utf-8")
      event.response.write(status,
        encBody,
        "text/plain; charset=UTF-8",
        SockoUtils.toSockoHeaderFormat(event.nettyHttpRequest.getMethod.name(), getResponseHeaders(event.request.headers), encBody.size)
      )
    }

    log.debug(e.getMessage, e)
    e match {
      case ce: ParseException =>
        writeErrorResponse(HttpResponseStatus.BAD_REQUEST, s"Invalid JSON - ${ce.getMessage}")
      case ce: SockoCommandException =>
        ce.msg == null || ce.msg == "" match {
          case true => writeErrorResponse(ce.code)
          case false => writeErrorResponse(ce.code, ce.msg)
        }
      case ce:CommandException =>
        writeErrorResponse(HttpResponseStatus.BAD_REQUEST, s"Command Exception - ${ce.getMessage}\n\t${ce.toString}")
      case arg:IllegalArgumentException =>
        writeErrorResponse(HttpResponseStatus.BAD_REQUEST, s"Illegal Arguments - ${arg.getMessage}\n\t${arg.toString}")
      case me:MappingException =>
        writeErrorResponse(HttpResponseStatus.BAD_REQUEST, s"Mapping Exception - ${me.getMessage}\n\t${me.toString}")
      case ex:Exception =>
        writeErrorResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, s"Server Error - ${ex.getMessage}\n\t${ex.toString}")
    }
  }

  /**
   * Function will return all the allowed headers for the command. Basically if you mixin a trait like
   * SprayGet, it will add the Get method to the allow header. This method should be overridden if you
   * are using the SprayCustom trait as then you would be defining your own routes and methods.
   *
   * @return
   */
  protected def getMethods = {
    def getMethod[I<:SockoRoutes](klass:Class[I], method:String) : Option[String] = {
      klass.isAssignableFrom(this.getClass) match {
        case true => Some(method)
        case false => None
      }
    }

    Seq[Option[String]] (
      getMethod(classOf[SockoGet], "GET"),
      getMethod(classOf[SockoHead], "HEAD"),
      getMethod(classOf[SockoPut], "PUT"),
      getMethod(classOf[SockoPost], "POST"),
      getMethod(classOf[SockoOptions], "OPTIONS"),
      getMethod(classOf[SockoDelete], "DELETE")
    ).flatten
  }

  def innerExecute[T<:AnyRef:Manifest](bean:SockoCommandBean) = {
    try {
      applyQSMap(bean)
      execute(Some(bean)).mapTo[CommandResponse[T]] onComplete {
        case Success(resp) =>
          resp.data match {
            case Some(value) =>
              val content = if (bean.event.endPoint.method.equalsIgnoreCase("head")) Array[Byte]() else marshallObject(value, resp.responseType)
              bean.event.response.write(
                responseStatusCode,
                // just double check the end point method, and if it is head then simply remove any content from the response
                content,
                SockoUtils.normalizeContentType(resp.responseType),
                SockoUtils.toSockoHeaderFormat(bean.event.nettyHttpRequest.getMethod.name(), getResponseHeaders(bean.event.request.headers), content.length)
              )
            case None => bean.event.response.write(HttpResponseStatus.NO_CONTENT)
          }
        case Failure(f) => f match {
          case ce: SockoCommandException => getRejectionHandler(bean.event, ce)
          case _ => bean.event.response.write(HttpResponseStatus.INTERNAL_SERVER_ERROR, f.getMessage)
        }
      }
    } catch {
      case e:Throwable => getRejectionHandler(bean.event, e)
    }
  }

  private[route] def addAutoHandler(handler:(HttpRequestEvent, mutable.HashMap[String, AnyRef]) => Unit, matcher:(HttpRequestEvent) => Option[mutable.HashMap[String, AnyRef]], method: String) = {
    val msg = ComponentMessage(
      AddSockoHandler(s"$commandName",
                      new AutoSockoHandler(matcher, handler), method),
      Some(ComponentManager.ComponentRef)
    )
    componentMessage("wookiee-socko", msg)
  }
}

trait SockoCustom extends SockoRoutes {
  this:Command =>

  List("GET", "HEAD", "PUT", "POST", "OPTIONS", "DELETE") foreach { method =>
    addAutoHandler(
      (event:HttpRequestEvent, bean:mutable.HashMap[String, AnyRef]) => {
        customHandler(event, bean)
      },
      (event:HttpRequestEvent) => {
        customMatcher(event)
      },
      method
    )
  }

  def customHandler(event: HttpRequestEvent, bean: mutable.HashMap[String, AnyRef])

  def customMatcher(event: HttpRequestEvent) : Option[mutable.HashMap[String, AnyRef]]
}

trait SockoGet extends SockoRoutes {
  this:Command =>

  addAutoHandler(
    (event:HttpRequestEvent, bean:mutable.HashMap[String, AnyRef]) => {
      innerExecute(SockoCommandBean(bean.toMap, event))
    },
    (event:HttpRequestEvent) => {
      defaultPathMatcher(event, paths)
    },
    "GET"
  )
}

trait EntityRoutes extends SockoRoutes {
  this:Command =>

  private[route] def addEntityToBean[T<:AnyRef:Manifest](bean:SockoCommandBean, obj:Array[Byte]) = {
    val conType = bean.headers.get("Content-Type") match {
      case null => "application/x-www-form-urlencoded"
      case x: String => x
    }

    var content = obj
    val contentEncoding =  bean.headers.get("Content-Encoding")
    contentEncoding match {
      case "gzip" =>
        content = Source.fromInputStream(new GZIPInputStream(new ByteArrayInputStream(content))).mkString.getBytes
      case "deflate" =>
        content = Source.fromInputStream(new InflaterInputStream(new ByteArrayInputStream(content))).mkString.getBytes
      case _ =>
    }

    bean.put(SockoCommandBean.Content, content)
    unmarshall(content, conType) match {
      case Some(v) => bean.appendMap(Map(CommandBean.KeyEntity -> v))
      case None => //ignore
    }
  }

  def unmarshall[T<:AnyRef:Manifest](data: Array[Byte], contentTypeValue: String = "application/json"): Option[T] = {
    if (data == null || data.length == 0) {
      None
    } else {

      val (contentType, charset) = parseContentType(contentTypeValue)

      contentType match {

        case "application/x-www-form-urlencoded" | "multipart/form-data" =>
          val dataString = new String(data, charset).dropWhile( _ == '?')
          val jObj = JObject(new QueryStringDecoder("?" + dataString, charset).parameters.map { kvp =>
            JField(kvp._1, if (kvp._2 != null) new JString(kvp._2.last) else JNull)
            }.toList)
          Some(jObj.extract[T])

        case "application/json" =>
          Some(JsonParser.parse(new String(data, "utf-8")).extract[T])

        case _ =>
          val dataString = new String(data, charset)
          Some(JObject(List(JField("content", new JString(dataString)))).extract[T])
      }
    }
  }

  def parseContentType(ct: String): (String, Charset) = {

    val parts = ct.split(";").toList.map{part => part.toLowerCase.trim}

    val contentType = if (parts.length > 0 && !parts(0).isEmpty) parts(0) else "text/plain"

    val charSet = if (parts.length >= 2 && parts(1).startsWith("charset=")) {
      Charset.forName(parts(1).split("=")(1))
    }
    else {
      CharsetUtil.UTF_8
    }

    (contentType, charSet)
  }
}

trait SockoPost extends EntityRoutes {
  this:Command =>

  addAutoHandler(
    (event:HttpRequestEvent, bean:mutable.HashMap[String, AnyRef]) => {
      val sBean = SockoCommandBean(bean.toMap, event)
      try {
        addEntityToBean[JObject](sBean, sBean.event.request.content.toBytes)
        innerExecute(sBean)
      } catch {
        case ex: Throwable => getRejectionHandler(sBean.event, ex)
      } finally {
        // Work around for Socko issue https://github.com/mashupbots/socko/issues/111
        try {
          event.request.content.toByteBuf.release(1)
        } catch {
          case _ => // Silently Ignore. If this fails, we don't have anything to release anyways.
        }
      }
    },
    (event:HttpRequestEvent) => {
      defaultPathMatcher(event, paths)
    },
    "POST"
  )
}

trait SockoDelete extends SockoRoutes {
  this:Command =>

  addAutoHandler(
    (event:HttpRequestEvent, bean:mutable.HashMap[String, AnyRef]) => {
      innerExecute(SockoCommandBean(bean.toMap, event))
    },
    (event:HttpRequestEvent) => {
      defaultPathMatcher(event, paths)
    },
    "DELETE"
  )
}

trait SockoOptions extends SockoRoutes {
  this:Command =>

  /**
   * Override this function to give the options specific information about the command
   *
   * @return
   */
  def sockoOptionsResponse : Any =  ""
  def sockoOptionResponseType : String = "text"

  addAutoHandler(
    (event:HttpRequestEvent, bean:mutable.HashMap[String, AnyRef]) => {
      // build the Allow and Access-Control-Allow-Methods headers
      val sBean = SockoCommandBean(bean.toMap, event)
      val methods = getMethods.mkString(", ")
      val optionsHeaders = Map[String, String](
        "Allow" -> methods,
        "Access-Control-Allow-Methods" -> methods
      )
      val cType = SockoUtils.normalizeContentType(sockoOptionResponseType)
      val content = marshallObject(sockoOptionsResponse, cType)
      sBean.event.response.write(
        HttpResponseStatus.OK,
        content,
        cType,
        SockoUtils.toSockoHeaderFormat("OPTIONS", getResponseHeaders(sBean.event.request.headers)) ++ optionsHeaders
      )
    },
    (event:HttpRequestEvent) => {
      defaultPathMatcher(event, paths)
    }, "OPTIONS"
  )
}

trait SockoHead extends SockoRoutes {
  this:Command =>

  addAutoHandler(
    (event:HttpRequestEvent, bean:mutable.HashMap[String, AnyRef]) => {
      innerExecute(SockoCommandBean(bean.toMap, event))
    },
    (event:HttpRequestEvent) => {
      defaultPathMatcher(event, paths)
    },
    "HEAD"
  )
}

trait SockoPut extends EntityRoutes {
  this:Command =>

  addAutoHandler(
    (event:HttpRequestEvent, bean:mutable.HashMap[String, AnyRef]) => {
      val sBean = SockoCommandBean(bean.toMap, event)
      try {
        addEntityToBean[JObject](sBean, sBean.event.request.content.toBytes)
        innerExecute(sBean)
      } catch {
        case ex: Throwable => getRejectionHandler(sBean.event, ex)
      } finally {
        // Work around for Socko issue https://github.com/mashupbots/socko/issues/111
        try {
          event.request.content.toByteBuf.release(1)
        } catch {
          case _ => // Silently Ignore. If this fails, we don't have anything to release anyways.
        }
      }
    },
    (event:HttpRequestEvent) => {
      defaultPathMatcher(event, paths)
    },
    "PUT"
  )
}
