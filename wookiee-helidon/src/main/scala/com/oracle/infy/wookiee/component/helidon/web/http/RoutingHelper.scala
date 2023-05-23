package com.oracle.infy.wookiee.component.helidon.web.http

import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointType.{
  BOTH,
  EXTERNAL,
  EndpointType,
  INTERNAL
}
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.WookieeResponse
import com.oracle.infy.wookiee.component.helidon.web.http.RoutingHelper.ServiceHolder
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter
import com.oracle.infy.wookiee.component.helidon.web.ws.HelidonWebsocket
import io.helidon.webserver._
import io.helidon.webserver.tyrus.TyrusSupport

import javax.websocket.server.ServerEndpointConfig
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object RoutingHelper {
  case class ServiceHolder(server: WebServer, routingService: WookieeRouter)

  // Takes the endpoints path template (e.g. /api/$foo/$bar) and the actual segments
  // that were sent (e.g. /api/v1/v2) and returns the Map of segment values (e.g. Map(foo -> v1, bar -> v2))
  def getPathSegments(pathWithVariables: String, actualSegments: List[String]): Map[String, String] = {
    val pathSegments = pathWithVariables.split("/")
    pathSegments
      .zip(actualSegments)
      .collect {
        case (segment, actual) if segment.startsWith("$") => (segment.drop(1), actual)
      }
      .toMap
  }

  def handleErrorAndRespond[Input <: Product: ClassTag: TypeTag, Output: ClassTag: TypeTag](
      errorHandler: Throwable => WookieeResponse,
      res: ServerResponse,
      e: Throwable
  ): Unit = {
    // Go into error handling
    val response = errorHandler(e)
    res.status(response.statusCode.code)
    response.headers.headers.foreach(x => res.headers().add(x._1, x._2.asJava))
    res.send(response.content.value)
    ()
  }
}

private[helidon] trait RoutingHelper {
  protected var internalServer: ServiceHolder = _
  protected var externalServer: ServiceHolder = _

  def internalPort: Int
  def externalPort: Int

  def startService(): Unit = {
    def startServer(routingService: WookieeRouter): ServiceHolder = {
      val routing = Routing
        .builder()
        .register("/", routingService)
        .register("/websocket", TyrusSupport.builder().build()) // Add Tyrus support for WebSocket
        .build()

      val server = WebServer
        .builder()
        .routing(routing)
        .port(internalPort)
        .build()

      server.start()
      ServiceHolder(server, routingService)
    }

    internalServer = startServer(new WookieeRouter())
    externalServer = startServer(new WookieeRouter())
  }

  def stopService(): Unit = {
    internalServer.server.shutdown()
    externalServer.server.shutdown()
    ()
  }

  def registerEndpoint(path: String, endpointType: EndpointType, method: String, handler: WookieeRouter.Handler): Unit =
    endpointType match {
      case EXTERNAL =>
        externalServer.routingService.addRoute(path, method, handler)
      case INTERNAL =>
        internalServer.routingService.addRoute(path, method, handler)
      case BOTH =>
        externalServer.routingService.addRoute(path, method, handler)
        internalServer.routingService.addRoute(path, method, handler)
    }

  def registerWebsocket(path: String, endpointType: EndpointType, websocket: HelidonWebsocket): Unit = {
    val endpointConfig = ServerEndpointConfig.Builder.create(websocket.getClass, path).build()
    endpointType match {
      case EXTERNAL =>
        externalServer.routingService.addWebsocketEndpoint(endpointConfig)
      case INTERNAL =>
        internalServer.routingService.addWebsocketEndpoint(endpointConfig)
      case BOTH =>
        externalServer.routingService.addWebsocketEndpoint(endpointConfig)
        internalServer.routingService.addWebsocketEndpoint(endpointConfig)
    }
  }
}
