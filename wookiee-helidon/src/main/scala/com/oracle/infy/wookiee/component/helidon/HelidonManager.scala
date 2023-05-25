package com.oracle.infy.wookiee.component.helidon

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.component.ComponentV2
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointType.{BOTH, EXTERNAL, EndpointType, INTERNAL}
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.helidon.web.http.WookieeHttpHandler
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter.{ServiceHolder, handlerFromCommand}
import com.typesafe.config.Config
import io.helidon.webserver._
import io.helidon.webserver.tyrus.TyrusSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object HelidonManager extends Mediator[HelidonManager] {
  val ComponentName = "wookiee-helidon"

  def registerEndpoint(command: WookieeHttpHandler)(implicit config: Config, ec: ExecutionContext): Unit = {
    val mediator = getMediator(config)
    WookieeCommandExecutive.getMediator(config).registerCommand(command)
    val handler = handlerFromCommand(command)

    mediator.registerEndpoint(command.path, command.endpointType, command.method, handler)
  }

  def registerEndpoint[Input <: Product: ClassTag: TypeTag, Output: ClassTag: TypeTag](
      name: String,
      path: String,
      method: String,
      endpointType: EndpointType.EndpointType,
      requestHandler: WookieeRequest => Future[Input],
      businessLogic: Input => Future[Output],
      responseHandler: Output => WookieeResponse,
      errorHandler: Throwable => WookieeResponse,
      endpointOptions: EndpointOptions = EndpointOptions.default
  )(
      implicit config: Config,
      ec: ExecutionContext
  ): Unit = {

    val cmdName = name
    val cmdMethod = method.toUpperCase
    val cmdPath = path
    val cmdType = endpointType
    val cmdErrors = errorHandler
    val cmdOptions = endpointOptions
    val command = new WookieeHttpHandler {
      override def commandName: String = cmdName
      override def method: String = cmdMethod
      override def path: String = cmdPath
      override def endpointType: EndpointType = cmdType
      override def errorHandler(ex: Throwable): WookieeResponse = cmdErrors(ex)
      override def endpointOptions: EndpointOptions = cmdOptions

      override def execute(input: WookieeRequest): Future[WookieeResponse] = {
        requestHandler(input)
          .flatMap(businessLogic)
          .map(responseHandler)
      }
    }

    registerEndpoint(command)
  }
}

class HelidonManager(name: String, config: Config) extends ComponentV2(name, config) {
  HelidonManager.registerMediator(config, this)

  protected[oracle] var internalServer: ServiceHolder = _
  protected[oracle] var externalServer: ServiceHolder = _

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

  def registerEndpoint(path: String, endpointType: EndpointType, method: String, handler: Handler): Unit =
    endpointType match {
      case EXTERNAL =>
        externalServer.routingService.addRoute(path, method, handler)
      case INTERNAL =>
        internalServer.routingService.addRoute(path, method, handler)
      case BOTH =>
        externalServer.routingService.addRoute(path, method, handler)
        internalServer.routingService.addRoute(path, method, handler)
    }

//  def registerWebsocket(path: String, endpointType: EndpointType, websocket: HelidonWebsocket): Unit = {
//    val endpointConfig = ServerEndpointConfig.Builder.create(websocket.getClass, path).build()
//    endpointType match {
//      case EXTERNAL =>
//        externalServer.routingService.addWebsocketEndpoint(endpointConfig)
//      case INTERNAL =>
//        internalServer.routingService.addWebsocketEndpoint(endpointConfig)
//      case BOTH =>
//        externalServer.routingService.addWebsocketEndpoint(endpointConfig)
//        internalServer.routingService.addWebsocketEndpoint(endpointConfig)
//    }
//  }

  override def start(): Unit = {
    startService()
    println(s"Helidon Servers started on ports: [internal=$internalPort], [external=$externalPort]")
  }

  override def prepareForShutdown(): Unit = {
    stopService()
    println("Helidon Server shutdown complete")
  }

}
