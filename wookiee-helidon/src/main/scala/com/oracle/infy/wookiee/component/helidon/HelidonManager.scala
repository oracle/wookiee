package com.oracle.infy.wookiee.component.helidon

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.component.ComponentV2
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointType.{
  BOTH,
  EXTERNAL,
  EndpointType,
  INTERNAL
}
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter.ServiceHolder
import com.oracle.infy.wookiee.component.helidon.web.ws.HelidonWebsocket
import com.typesafe.config.Config
import io.helidon.webserver._
import io.helidon.webserver.tyrus.TyrusSupport

import javax.websocket.server.ServerEndpointConfig
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object HelidonManager extends Mediator[HelidonManager] {
  val ComponentName = "wookiee-helidon"

  def registerEndpoint[Input <: Product: ClassTag: TypeTag, Output: ClassTag: TypeTag](
      // TODO Stuff this into a Command with this name
      name: String,
      path: String,
      method: String,
      endpointType: EndpointType.EndpointType,
      requestHandler: WookieeRequest => Future[Input],
      businessLogic: Input => Future[Output],
      responseHandler: Output => WookieeResponse,
      errorHandler: Throwable => WookieeResponse,
      // TODO Incorporate options
      endpointOptions: EndpointOptions = EndpointOptions.default
  )(
      implicit config: Config,
      ec: ExecutionContext
  ): Unit = {
    val mediator = getMediator(config)
    val handler: Handler = (req, res) =>
      try {
        val actualPath = req.path().toString.split("\\?").head
        val pathSegments = WookieeRouter.getPathSegments(path, actualPath)

        // Get the query parameters on the request
        val queryParams = req.queryParams().toMap.asScala.toMap.map(x => x._1 -> x._2.asScala.toList)
        req.content().as(classOf[Array[Byte]]).thenAccept { bytes =>
          val wookieeRequest = WookieeRequest(
            Content(bytes),
            pathSegments,
            queryParams,
            Headers(req.headers().toMap.asScala.toMap.map(x => x._1 -> x._2.asScala.toList))
          )

          requestHandler(wookieeRequest)
            .flatMap(businessLogic)
            .map(responseHandler)
            .map { response =>
              response.headers.mappings.foreach(x => res.headers().add(x._1, x._2.asJava))
              res.status(response.statusCode.code)
              res.send(response.content.value)
            }
            .recover {
              case e: Throwable =>
                WookieeRouter.handleErrorAndRespond[Input, Output](errorHandler, res, e)
            }
          ()
        }

        ()
      } catch {
        case e: Throwable =>
          WookieeRouter.handleErrorAndRespond[Input, Output](errorHandler, res, e)
      }

    mediator.registerEndpoint(path, endpointType, method, handler)
  }
}

class HelidonManager(name: String, config: Config) extends ComponentV2(name, config) {
  HelidonManager.registerMediator(config, this)

  protected var internalServer: ServiceHolder = _
  protected var externalServer: ServiceHolder = _

  def internalPort: Int = config.getInt(s"${HelidonManager.ComponentName}.web.internal-port")
  def externalPort: Int = config.getInt(s"${HelidonManager.ComponentName}.web.external-port")

  def startService(): Unit = {
    def startServer(routingService: WookieeRouter, port: Int): ServiceHolder = {
      val routing = Routing
        .builder()
        .register("/", routingService)
        .register("/websocket", TyrusSupport.builder().build()) // Add Tyrus support for WebSocket
        .build()

      val server = WebServer
        .builder()
        .routing(routing)
        .port(port)
        .build()

      server.start()
      ServiceHolder(server, routingService)
    }

    internalServer = startServer(new WookieeRouter(), internalPort)
    externalServer = startServer(new WookieeRouter(), externalPort)
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

  override def start(): Unit = {
    startService()
    println(s"Helidon Servers started on ports: [internal=$internalPort], [external=$externalPort]")
  }

  override def prepareForShutdown(): Unit = {
    stopService()
    println("Helidon Server shutdown complete")
  }

}
