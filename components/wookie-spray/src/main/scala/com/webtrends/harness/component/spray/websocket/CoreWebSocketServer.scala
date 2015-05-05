package com.webtrends.harness.component.spray.websocket

import java.net.InetAddress

import akka.actor.Props
import akka.io.IO
import akka.pattern.ask
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.spray.WebSocketRunning
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import spray.can.Http
import spray.can.server.{Stats, ServerSettings, UHttp}
import scala.concurrent.{Await, Promise, Future}
import scala.util.{Try, Success, Failure}
import scala.concurrent.duration._

@SerialVersionUID(1L) case class WebSocketBindServer()
@SerialVersionUID(1L) case class WebSocketShutdownServer()

/**
 * Created by wallinm on 3/27/15.
 */
class CoreWebSocketServer(port:Int, settings:Option[ServerSettings]=None) extends HActor {
  import context.system

  override def receive: Receive = binding

  def binding: Receive = {
    case WebSocketBindServer => bindWebSocketServer()
  }

  def running: Receive = health orElse {
    // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
    case Http.Connected(remoteAddress, localAddress) =>
      log.debug("WebSocket connected")
      val serverConnection = sender()
      val worker = context.actorOf(CoreWebSocketWorker.props(serverConnection))
      serverConnection ! Http.Register(worker)
    case WebSocketShutdownServer =>
      shutdownWebSocketServer()
    case x =>
      log.debug("Unkown message: " + x)
  }

  val server = IO(UHttp)

  def bindWebSocketServer(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    log.info(s"Trying to bind websocket server to port $port")

    (server ? Http.Bind(self, interface = "0.0.0.0", port = port, settings = settings)) onComplete {
      case Failure(e) =>
        log.error(s"Error trying to bind websocket server to port $port", e)
        throw e
      case Success(s) =>
        context.become(running)
        context.parent ! WebSocketRunning
    }
  }

  /**
   * Unbind from the http service
   */
  protected def shutdownWebSocketServer(): Unit = {
    log.info(s"Trying to shutdown the websocket server ${InetAddress.getLocalHost.getHostName} on port $port")
    val f = server ? Http.CloseAll
    Try(Await.result(f, 1 second)).recover({
      case e: Throwable =>
        log.warning("we were unable to properly shutdown the websocket server: {}", e.getMessage)
    })

    log.info("WebSocket server shutdown complete")
    context.become(binding)
  }

  override protected def getHealth: Future[HealthComponent] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val future = system.actorSelection(server.path.toString.concat("/listener-0")) ? Http.GetStats
    val p = Promise[HealthComponent]()

    future.onComplete {
      case f: Failure[_] =>
        p complete Success(HealthComponent(
          "websocket-server",
          ComponentState.CRITICAL,
          "The internal websocket server is not running: " + f.exception.getMessage,
          None)
        )
      case s: Success[_] =>
        p complete Success(HealthComponent(
          "websocket-server",
          ComponentState.NORMAL,
          s"The internal websocket server is running on port $port",
          Some(s.value.asInstanceOf[Stats]))
        )
    }

    p.future
  }
}

object CoreWebSocketServer {
  def props(port:Int, settings:Option[ServerSettings]=None) = Props(classOf[CoreWebSocketServer], port, settings)
}
