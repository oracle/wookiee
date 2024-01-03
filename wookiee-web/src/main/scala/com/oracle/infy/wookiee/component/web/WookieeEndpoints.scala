package com.oracle.infy.wookiee.component.web

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.component.metrics.TimerStopwatch
import com.oracle.infy.wookiee.component.web.http.HttpCommand
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.web.http.HttpObjects.{
  EndpointOptions,
  EndpointType,
  WookieeRequest,
  WookieeResponse
}
import com.oracle.infy.wookiee.component.web.http.impl.WookieeRouter.{WebsocketHandler, handlerFromCommand}
import com.oracle.infy.wookiee.component.web.ws.{WebsocketInterface, WookieeWebsocket}
import com.typesafe.config.Config

import javax.websocket.Session
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

/**
  * This class is the main helper for registering HTTP and WS endpoints with Wookiee Helidon.
  * Examples of usage for each of these methods can be found in the 'advanced-communication' example project.
  *
  * For both HTTP and WS there are provided both 'object-oriented' and 'functional' entry points.
  */
object WookieeEndpoints {

  /**
    * Primary 'object-oriented' entry point for registering an HTTP endpoint using Wookiee Helidon.
    * Will pull various functions and properties off of the WookieeHttpHandler and use them to
    * construct a handler registered at the specified path
    */
  def registerEndpoint(command: => HttpCommand)(implicit config: Config, ec: ExecutionContext): Unit = {
    val mediator = WebManager.getMediator(config)
    // Just one routee, can call this directly afterwards to register more
    WookieeCommandExecutive.getMediator(config).registerCommand(command, 1)
    val instance = WookieeActor.actorOf(command)
    val handler = handlerFromCommand(instance)

    mediator.registerEndpoint(instance.path, instance.endpointType, instance.method, handler)
  }

  /**
    * Primary 'functional' entry point for registering an HTTP endpoint using Wookiee Helidon.
    * Will compose the input functions in order of their passing into a WookieeHttpHandler object.
    * This object will then be automatically hosted on the specified web server
    */
  def registerEndpoint[Input <: Product: ClassTag: TypeTag, Output: ClassTag: TypeTag](
      name: String, // Unique name that will also expose this command via the WookieeCommandExecutive
      path: String, // HTTP path to host this endpoint, segments starting with '$' will be treated as wildcards
      method: String, // e.g. GET, POST, PATCH, OPTIONS, etc.
      endpointType: EndpointType.EndpointType, // Host this endpoint on internal or external port?
      requestHandler: WookieeRequest => Future[Input], // Marshall the WookieeRequest into any generic Input type
      businessLogic: Input => Future[Output], // Main business logic, Output is any generic type
      responseHandler: Output => WookieeResponse, // Marshall the generic Output type into a WookieeResponse
      errorHandler: (WookieeRequest, Throwable) => WookieeResponse, // If any errors happen at any point, how shall we respond
      endpointOptions: EndpointOptions = EndpointOptions.default // Set of options including CORS allowed headers
  )(
      implicit config: Config, // This just need to have 'instance-id' set to any string
      ec: ExecutionContext
  ): Unit = {

    val cmdName = name
    val cmdMethod = method.toUpperCase
    val cmdPath = path
    val cmdType = endpointType
    val cmdErrors = errorHandler
    val cmdOptions = endpointOptions
    registerEndpoint(new HttpCommand {
      override val name: String = commandName

      override def commandName: String = cmdName

      override def method: String = cmdMethod

      override def path: String = cmdPath

      override def endpointType: EndpointType = cmdType

      override def errorHandler(wookieeRequest: WookieeRequest, ex: Throwable): WookieeResponse =
        cmdErrors(wookieeRequest, ex)

      override def endpointOptions: EndpointOptions = cmdOptions

      override def execute(input: WookieeRequest): Future[WookieeResponse] = {
        maybeTimeF(endpointOptions.requestHandlerTimerLabel, requestHandler(input))
          .flatMap(input => maybeTimeF(endpointOptions.businessLogicTimerLabel, businessLogic(input)))
          .map(
            output =>
              endpointOptions
                .responseHandlerTimerLabel
                .map(label => {
                  TimerStopwatch.tryWrapper(label)(responseHandler(output))
                })
                .getOrElse(responseHandler(output))
          )
      }
    })
  }

  // Primary 'functional' entry point for registering an WS endpoint using Wookiee Helidon.
  def registerWebsocket[Auth <: Any: ClassTag](
      path: String, // WS path to host this endpoint, segments starting with '$' will be treated as wildcards
      endpointType: EndpointType, // Host this endpoint on internal or external port?
      // Main business logic of the websocket, this will be called for every message received
      // Use the attached WebsocketInterface to send messages back to the client or close the websocket
      handleInMessage: (String, WebsocketInterface, Option[Auth]) => Unit,
      // Auth handling logic, this object will be passed along to `handleInMessage`, called once after handshake
      // If this fails or errors, we'll close the websocket immediately
      authHandler: WookieeRequest => Future[Option[Auth]] = (_: WookieeRequest) => Future.successful(None),
      // When this websocket is closed for any reason, this will be invoked
      onCloseHandler: Option[Auth] => Unit = (_: Option[Auth]) => (),
      // When this an error happens anywhere in the websocket, this will be invoked
      wsErrorHandler: (WebsocketInterface, String, Option[Auth]) => Throwable => Unit =
      (_: WebsocketInterface, _: String, _: Option[Auth]) => { _: Throwable => () },
      // Set of options including CORS allowed headers
      endpointOptions: EndpointOptions = EndpointOptions.default
  )(implicit config: Config): Unit = {
    val wsPath = path
    val wsEndpointType = endpointType
    val wsEndpointOptions = endpointOptions

    val websocket = new WookieeWebsocket[Auth] {
      override def path: String = wsPath

      override def endpointType: EndpointType = wsEndpointType

      override def endpointOptions: EndpointOptions = wsEndpointOptions

      override def onClosing(auth: Option[Auth]): Unit =
        onCloseHandler(auth)

      override def handleError(request: WookieeRequest, message: String, authInfo: Option[Auth])(
          implicit session: Session
      ): Throwable => Unit =
        wsErrorHandler(new WebsocketInterface(request), message, authInfo)

      override def handleAuth(request: WookieeRequest): Future[Option[Auth]] =
        authHandler(request)

      override def handleText(text: String, request: WookieeRequest, authInfo: Option[Auth])(
          implicit session: Session
      ): Unit =
        handleInMessage(text, new WebsocketInterface(request), authInfo)
    }

    registerWebsocket(websocket)
  }

  // Primary 'object-oriented' entry point for registering an WS endpoint using Wookiee Helidon.
  def registerWebsocket[Auth <: Any: ClassTag](
      helidonWebsocket: WookieeWebsocket[Auth]
  )(implicit config: Config): Unit = {
    val mediator = WebManager.getMediator(config)
    mediator.registerEndpoint(
      helidonWebsocket.path,
      helidonWebsocket.endpointType,
      "WS",
      WebsocketHandler(helidonWebsocket)
    )
  }

  protected[oracle] def maybeTimeF[T](timerLabel: Option[String], toRun: => Future[T])(
      implicit ec: ExecutionContext
  ): Future[T] = {
    timerLabel match {
      case Some(label) =>
        TimerStopwatch.futureWrapper(label)({
          toRun
        })
      case None =>
        toRun
    }
  }
}
