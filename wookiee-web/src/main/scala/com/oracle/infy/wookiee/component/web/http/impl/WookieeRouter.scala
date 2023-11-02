package com.oracle.infy.wookiee.component.web.http.impl

import com.oracle.infy.wookiee.component.web.http.HttpCommand
import com.oracle.infy.wookiee.component.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.web.ws.WookieeWebsocket
import com.oracle.infy.wookiee.component.web.ws.tyrus.{WookieeTyrusContainer, WookieeTyrusHandler}
import com.oracle.infy.wookiee.component.web.{AccessLog, WookieeEndpoints}
import com.oracle.infy.wookiee.logging.LoggingAdapter
import io.helidon.webserver._
import org.glassfish.tyrus.ext.extension.deflate.PerMessageDeflateExtension

import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.websocket.HandshakeResponse
import javax.websocket.server.{HandshakeRequest, ServerEndpointConfig}
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/**
  * This support object for WookieeRouter contains the handler creation logic that Helidon needs
  * for HTTP endpoint registration. It also has helpful functions for error handling, path segmentation,
  * and other routing related tasks.
  */
object WookieeRouter extends LoggingAdapter {
  // Represents the entire HTTP service and routing layers
  case class ServiceHolder(server: WebServer, routingService: WookieeRouter)

  // Wraps a Helidon handler and the options for that endpoint
  trait WookieeHandler {
    val endpointOptions: EndpointOptions
  }

  case class HttpHandler(handler: Handler, endpointOptions: EndpointOptions = EndpointOptions.default)
      extends WookieeHandler

  case class WebsocketHandler[Auth <: Any: ClassTag](handler: WookieeWebsocket[Auth]) extends WookieeHandler {
    override val endpointOptions: EndpointOptions = handler.endpointOptions
  }

  val FLUSH_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
  val REQUEST_HEADERS: String = "RequestHeaders"

  case class EndpointMeta(
      path: String,
      method: String
  )

  // Primary class of our Path Trie
  case class PathNode(
      // Will descend down the paths with a map at each segment
      children: ConcurrentHashMap[String, PathNode],
      // Will contain HTTP methods and their handlers for a specific path
      handlers: ConcurrentHashMap[String, WookieeHandler] = new ConcurrentHashMap[String, WookieeHandler]()
  )

  // Takes the endpoints path template (e.g. /api/$foo/$bar) and the actual segments
  // that were sent (e.g. /api/v1/v2) and returns the Map of segment values (e.g. Map(foo -> v1, bar -> v2))
  def getPathSegments(pathWithVariables: String, actualPath: String): Map[String, String] = {
    val pathSegments =
      if (pathWithVariables.trim.startsWith("/"))
        pathWithVariables.trim.split("/")
      else
        ("/" + pathWithVariables.trim).split("/")

    val actualSegments = actualPath.split("/")
    pathSegments
      .zip(actualSegments)
      .collect {
        case (segment, actual) if segment.startsWith("$") => (segment.drop(1), actual)
      }
      .toMap
  }

  // This method is meant to wrap the error handling logic for an individual endpoint
  // in yet another try/catch block. This is because the error handling logic itself
  // can throw an error, and we want to make sure that we catch that and return a 500.
  def handleErrorAndRespond(
      errorHandler: Throwable => WookieeResponse,
      res: ServerResponse,
      e: Throwable
  ): Unit =
    try {
      // Go into error handling
      val response = errorHandler(e)
      res.status(response.statusCode.code)
      res.headers().add("Content-Type", response.contentType)
      response.headers.getMap.foreach(x => res.headers().add(x._1, x._2.asJava))
      res.send(response.content.value)
      ()
    } catch {
      case ex: Throwable =>
        log.error("RH400: Unexpected error in error handling logic while handling error", ex)
        log.error("RH401: Original error was:", e)
        res.status(500)
        res.send("There was an internal server error.")
        ()
    }

  // This is the conversion method that takes a WookieeHttpHandler command and converts it into
  // a Helidon Handler. This is the method that HelidonManager calls when it needs to register an endpoint.
  protected[oracle] def handlerFromCommand(command: HttpCommand)(implicit ec: ExecutionContext): HttpHandler =
    HttpHandler(
      { (req, res) =>
        // There are three catch and recovery points in this method as there are three different
        // scopes in which errors can occur
        try {
          val actualPath = req.path().toString.split("\\?").headOption.getOrElse("")
          val pathSegments = WookieeRouter.getPathSegments(command.path, actualPath)

          // Get the query parameters on the request, lowercase them
          val queryParams =
            req.queryParams().toMap.asScala.toMap.map(x => x._1.toLowerCase -> x._2.asScala.mkString(","))
          req.content().as(classOf[Array[Byte]]).thenAccept {
            bytes =>
              try {
                val wookieeRequest = WookieeRequest(
                  Content(bytes),
                  pathSegments,
                  queryParams,
                  Headers(req.headers().toMap.asScala.toMap.map(x => x._1 -> x._2.asScala.toList))
                )

                // Main execution logic for this HTTP command
                WookieeEndpoints
                  .maybeTimeF(command.endpointOptions.routeTimerLabel, {
                    command
                      .requestDirective(wookieeRequest)
                      .flatMap(command.execute)
                  })
                  .map {
                    response =>
                      val respHeaders = command.endpointOptions.defaultHeaders.getMap ++ response.headers.getMap
                      res.headers().add("Content-Type", response.contentType)
                      respHeaders.foreach(x => res.headers().add(x._1, x._2.asJava))
                      res.status(response.statusCode.code)
                      res.send(response.content.value)
                      AccessLog.logAccess(Some(wookieeRequest), command.method, actualPath, res.status().code)
                  }
                  .recover {
                    case e: Throwable =>
                      WookieeRouter.handleErrorAndRespond(command.errorHandler, res, e)
                      AccessLog.logAccess(Some(wookieeRequest), command.method, actualPath, res.status().code)
                  }
                ()
              } catch {
                case e: Throwable =>
                  WookieeRouter.handleErrorAndRespond(command.errorHandler, res, e)
                  AccessLog.logAccess(None, command.method, actualPath, res.status().code)
              }
          }

          ()
        } catch {
          case e: Throwable =>
            WookieeRouter.handleErrorAndRespond(command.errorHandler, res, e)
        }
      },
      command.endpointOptions
    )
}

/**
  * This class is the main router for the Wookiee Helidon HTTP service. It is responsible for
  * registering endpoints and routing/handling requests. The routing schema utilizes a Trie
  * data structure to allow for efficient routing of requests.
  */
case class WookieeRouter(allowedOrigins: CorsWhiteList = CorsWhiteList()) extends Service {
  import WookieeRouter._

  val root: PathNode = PathNode(new ConcurrentHashMap[String, PathNode]())
  // Tyrus container that holds websocket endpoints
  protected[oracle] val engine: WookieeTyrusContainer = new WookieeTyrusContainer()
  // Universal handler for all websocket requests
  protected[oracle] val websocketHandler: WookieeTyrusHandler = new WookieeTyrusHandler(engine.getWebSocketEngine)

  /**
    * This is the method that HelidonManager calls when it needs to register an endpoint.
    * It takes the path, method, and handler and adds it to the Trie of available endpoints.
    *
    * Any '$' prefixed values on the path are considered variables and will be treated
    * as wildcards when routing requests. For example, if you register an endpoint with the
    * path "/api/$foo/$bar" and a request comes in with the path "/api/v1/v2", the request
    * will be routed to that endpoint. Precedence will be given to exact matches, so a request
    * "/api/v1" will hit the endpoint registered with the path "/api/v1" instead of "/api/$baz".
    *
    * You can get the WookieeHandler object via WookieeRouter.handlerFromCommand
    */
  def addRoute(path: String, method: String, handler: WookieeHandler): Unit = root.synchronized {
    if (path.isEmpty) throw new IllegalArgumentException("Path cannot be empty")

    val segments = path.split("/").filter(_.nonEmpty)
    val upperMethod = method.toUpperCase

    // Navigate to the correct node in the Trie, creating nodes along the way if necessary
    var currentNode = root
    for (segment <- segments) {
      // If the segment starts with a '$', treat it as a wildcard
      val segOrWild = if (segment.startsWith("$")) "*" else segment
      if (!currentNode.children.containsKey(segOrWild)) {
        currentNode.children.put(segOrWild, PathNode(new ConcurrentHashMap[String, PathNode]()))
      }
      currentNode = currentNode.children.get(segOrWild)
    }

    // In the last segment, store the method and handler
    currentNode.handlers.put(upperMethod, handler)
    handler match {
      case wsHandler: WebsocketHandler[_] =>
        // This trick allows us to use an instantiated HelidonWebsocket
        val serverEndpointConfig = ServerEndpointConfig
          .Builder
          .create(wsHandler.handler.getEndpointInstance.getClass, convertPath(wsHandler.handler.path))
          .extensions(Collections.singletonList(new PerMessageDeflateExtension()))
          .configurator(new ServerEndpointConfig.Configurator {
            // This takes the headers from the request and passes them along to the websocket
            override def modifyHandshake(
                sec: ServerEndpointConfig,
                request: HandshakeRequest,
                response: HandshakeResponse
            ): Unit = {
              val headersMap = request.getHeaders.asScala.toMap.map { case (k, v) => (k, v.asScala.toList) }
              sec.getUserProperties.put(REQUEST_HEADERS, Headers(headersMap))
              ()
            }

            override def getEndpointInstance[T](endpointClass: Class[T]): T = {
              log.debug("Getting endpoint instance")
              wsHandler.handler.getEndpointInstance.asInstanceOf[T]
            }
          })
          .build()

        engine.register(serverEndpointConfig, "")
      case _ => // Normal HTTP handler
    }
    log.debug(s"WR100: Registered [$method] endpoint: [$path]")
  }

  // This is the search method for the underlying Trie. It is complicated by the
  // fact that we need to support path variables. This means that we need to
  // search down multiple paths in the Trie since some paths represent wildcards.
  def findHandlers(path: String): Map[String, WookieeHandler] = {
    // Strip off any trialing query params
    val withoutQueryParams = path.split("\\?").head
    val segments = withoutQueryParams.split("/").filter(_.nonEmpty)

    // Start at the root of the Trie
    var currentNode = Option(root)
    // Will be used to store wildcard nodes that we encounter
    // in case we need to backtrack
    val wildcardNodes = new ListBuffer[(PathNode, Int)]()
    var i = 0

    // Goes down the trie until we find a handler or we run out of trie
    while (i < segments.length && currentNode.isDefined) {
      currentNode match {
        case Some(PathNode(children, _)) if children.containsKey(segments(i)) =>
          currentNode = Some(children.get(segments(i)))
          if (children.containsKey("*"))
            wildcardNodes += ((children.get("*"), i))
          i += 1
        case Some(PathNode(children, _)) if children.containsKey("*") =>
          currentNode = Some(children.get("*"))
          i += 1
        case _ =>
          if (wildcardNodes.nonEmpty) {
            val (node, index) = wildcardNodes.remove(wildcardNodes.length - 1)
            currentNode = Some(node)
            i = index + 1
          } else {
            currentNode = None
          }
      }
    }

    // If we found a node, return its handlers. Otherwise, return None
    currentNode.map(node => node.handlers.asScala.toMap).getOrElse(Map.empty)
  }

  def findHandler(path: String, method: String): Option[WookieeHandler] = {
    val upperMethod = method.toUpperCase
    val handlers = findHandlers(path)

    // If we found a handler, return it. Otherwise, return None
    handlers.get(upperMethod)
  }

  // Will return a list of all registered routes and methods
  def listOfRoutes(): List[EndpointMeta] = {
    def navigateNode(node: PathNode, pathSoFar: String): List[EndpointMeta] = {
      val currentList = if (!node.handlers.isEmpty) {
        (node
          .handlers
          .asScala
          .map {
            case (method: String, _: WookieeHandler) =>
              EndpointMeta(pathSoFar, method)
          })
          .toList
      } else List()

      currentList ++ node.children.asScala.toList.flatMap {
        case (segment, child) =>
          navigateNode(child, s"$pathSoFar/$segment")
      }
    }

    navigateNode(root, "")
  }

  // This is a Helidon Service method that is called by that library
  // whenever a request comes into the WebServer. Calls findHandler mainly
  override def update(rules: Routing.Rules): Unit = {
    rules
      .any((req, res) => {
        val path = req.path().toString
        // Skip this handler if not an upgrade request
        val secWebSocketKey = req.headers().value(HandshakeRequest.SEC_WEBSOCKET_KEY)
        val method =
          if (secWebSocketKey.isEmpty)
            req.method().name().trim.toUpperCase
          else "WS" // This is an upgrade request

        // Origin of the request, header used by CORS
        val originOpt = Option(req.headers.value("Origin").orElse(null))

        def isOriginAllowed: Boolean = allowedOrigins match {
          case _: AllowAll      => true
          case allow: AllowSome =>
            // Return false if Origin wasn't on request
            originOpt.map(allow.allowed).exists(_.nonEmpty)
        }

        // Allow all if allowedHeaders is None
        def allowedHeaders(handlers: List[WookieeHandler], headers: List[String]): List[String] =
          if (handlers.isEmpty) List()
          else {
            val allowed: List[CorsWhiteList] = handlers.map(_.endpointOptions.allowedHeaders.getOrElse(CorsWhiteList()))
            headers.filter(head => allowed.forall(_.allowed(head).nonEmpty))
          }

        // Didn't match CORS Origin requirements
        if (!isOriginAllowed) {
          res.status(403).send("Origin not permitted.")
          AccessLog.logAccess(None, method, path, 403)
          return
        }

        // Main search function
        val handlers = findHandlers(path)
        handlers.get(method) match {
          case Some(httpHandler: HttpHandler) => // Normal handling
            res.headers().add("Access-Control-Allow-Origin", originOpt.getOrElse("*"))
            res.headers().add("Access-Control-Allow-Credentials", "true")
            httpHandler.handler.accept(req, res)

          case Some(wsHandler: WebsocketHandler[_]) => // Websocket handling
            res.headers().add("Access-Control-Allow-Origin", originOpt.getOrElse("*"))
            res.headers().add("Access-Control-Allow-Credentials", "true")
            websocketHandler.acceptWithContext(req, res, wsHandler.endpointOptions)

          case _ if handlers.nonEmpty && method == "OPTIONS" => // This is a CORS pre-flight request
            val reqHeadersOpt =
              Option(req.headers.value("Access-Control-Request-Headers").orElse(null))
                .map(_.split(",").map(_.trim).toList)
                .getOrElse(List())

            res.headers().add("Access-Control-Allow-Origin", originOpt.getOrElse("*"))
            res.headers().add("Access-Control-Allow-Methods", handlers.keySet.mkString(","))
            res.headers().add("Access-Control-Allow-Credentials", "true")
            val allowedHeads = allowedHeaders(handlers.values.toList, reqHeadersOpt)
            res.headers().add("Access-Control-Allow-Headers", allowedHeads: _*)
            res.status(200).send("")

          case _ =>
            res.status(404).send("Endpoint not found.")
        }
        ()
      })
    ()
  }

  // /api/$var/path --> /api/{var}/path
  private[wookiee] def convertPath(path: String): String =
    path.split("/").map(segment => if (segment.startsWith("$")) s"{${segment.drop(1)}}" else segment).mkString("/")
}
