package com.oracle.infy.wookiee.component.helidon.web.http.impl

import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter._
import io.helidon.webserver.{Routing, ServerRequest, ServerResponse, Service}

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import javax.websocket.server.ServerEndpointConfig

object WookieeRouter {
  type Handler = BiConsumer[ServerRequest, ServerResponse]

  case class PathNode(children: ConcurrentHashMap[String, PathNode], handler: Option[Handler] = None)
}

class WookieeRouter extends Service {
  val root = new ConcurrentHashMap[String, PathNode]()

  def addRoute(path: String, method: String, handler: Handler): Unit = {
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

  def findHandler(path: String, method: String): Option[Handler] = {
    val segments = path.split("/").filter(_.nonEmpty)
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
