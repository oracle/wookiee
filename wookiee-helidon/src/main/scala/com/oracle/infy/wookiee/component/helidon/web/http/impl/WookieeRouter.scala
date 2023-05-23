package com.oracle.infy.wookiee.component.helidon.web.http.impl

import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.WookieeResponse
import com.oracle.infy.wookiee.logging.LoggingAdapter
import io.helidon.webserver._

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import javax.websocket.server.ServerEndpointConfig
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object WookieeRouter extends LoggingAdapter {
  case class ServiceHolder(server: WebServer, routingService: WookieeRouter)

  type Handler = BiConsumer[ServerRequest, ServerResponse]

  case class PathNode(children: ConcurrentHashMap[String, PathNode], handler: Option[Handler] = None)

  // Takes the endpoints path template (e.g. /api/$foo/$bar) and the actual segments
  // that were sent (e.g. /api/v1/v2) and returns the Map of segment values (e.g. Map(foo -> v1, bar -> v2))
  def getPathSegments(pathWithVariables: String, actualPath: String): Map[String, String] = {
    val pathSegments = pathWithVariables.split("/")
    val actualSegments = actualPath.split("/")
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
  ): Unit =
    try {
      // Go into error handling
      val response = errorHandler(e)
      res.status(response.statusCode.code)
      response.headers.mappings.foreach(x => res.headers().add(x._1, x._2.asJava))
      res.send(response.content.value)
      ()
    } catch {
      case ex: Throwable =>
        log.error("RH400: Unexpected error in error handling logic while handling error", ex)
        log.error("RH401: Original error was:", e)
        res.status(500)
        res.send("Internal Server Error")
        ()
    }
}

class WookieeRouter extends Service {
  import WookieeRouter._

  val root = new ConcurrentHashMap[String, PathNode]()

  def addRoute(path: String, method: String, handler: WookieeRouter.Handler): Unit = {
    if (path.isEmpty) throw new IllegalArgumentException("Path cannot be empty")

    val segments = path.split("/").filter(_.nonEmpty)
    val upperMethod = method.toUpperCase
    if (!root.containsKey(upperMethod)) {
      root.put(upperMethod, PathNode(new ConcurrentHashMap[String, PathNode]()))
    }

    var currentNode = root.get(upperMethod)
    for (segment <- segments.init) {
      val segOrWild = if (segment.startsWith("$")) "*" else segment
      if (!currentNode.children.containsKey(segOrWild)) {
        currentNode.children.put(segOrWild, PathNode(new ConcurrentHashMap[String, PathNode]()))
      }
      currentNode = currentNode.children.get(segOrWild)
    }

    val finalSegment = if (segments.last.startsWith("$")) "*" else segments.last
    if (!currentNode.children.containsKey(finalSegment)) {
      currentNode.children.put(finalSegment, PathNode(new ConcurrentHashMap[String, PathNode](), Some(handler)))
    } else {
      val node = currentNode.children.get(finalSegment)
      currentNode.children.put(finalSegment, node.copy(handler = Some(handler)))
    }
    ()
  }

  def addWebsocketEndpoint(endpointConfig: ServerEndpointConfig): Unit = {
    // TODO
  }

  def findHandler(path: String, method: String): Option[WookieeRouter.Handler] = {
    val withoutQueryParams = path.split("\\?").head
    val segments = withoutQueryParams.split("/").filter(_.nonEmpty)
    val upperMethod = method.toUpperCase
    if (!root.containsKey(upperMethod)) {
      return None
    }
    var currentNode = Option(root.get(upperMethod))

    for (segment <- segments if currentNode.isDefined) {
      currentNode match {
        case Some(PathNode(children, _)) if children.containsKey(segment) =>
          currentNode = Some(children.get(segment))

        case Some(PathNode(children, _)) if children.containsKey("*") =>
          currentNode = Some(children.get("*"))

        case _ =>
          currentNode = None
      }
    }

    currentNode.flatMap {
      case PathNode(_, Some(handler)) => Some(handler)
      case _                          => None
    }
  }

  override def update(rules: Routing.Rules): Unit = {
    rules.any((req, res) => {
      val path = req.path().toString

      findHandler(path, req.method().name()) match {
        case Some(handler) => handler.accept(req, res)
        case None          => res.status(404).send()
      }
      ()
    })
    ()
  }
}
