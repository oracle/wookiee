package akka.http

import akka.http.impl.engine.server.InternalCustomHeader
import akka.http.javadsl.model.headers.AcceptEncoding
import akka.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Upgrade, UpgradeProtocol, `Sec-WebSocket-Protocol`}
import akka.http.scaladsl.model.ws.{Message, WebSocketUpgrade}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Flow
import akka.stream.{FlowShape, Graph, Materializer}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable

/**
  * This is taken from [https://github.com/akka/akka-http/blob/master/akka-http-testkit/src/main/scala/akka/http/scaladsl/testkit/WSTestRequestBuilding.scala]
  * NOTE: When upgrading akka-http if this breaks then slot in the new code and append .addHeader for encoding as seen at the end of the file
  */
trait WSWrapper extends AnyWordSpecLike with Matchers with ScalatestRouteTest {

  override def WS(uri: Uri, clientSideHandler: Flow[Message, Message, Any], subprotocols: Seq[String] = Nil)(
      implicit materializer: Materializer
  ): HttpRequest =
    WS(uri, clientSideHandler, None, subprotocols)

  def WS(
      uri: Uri,
      clientSideHandler: Flow[Message, Message, Any],
      encoding: Option[AcceptEncoding],
      subprotocols: Seq[String]
  )(implicit materializer: Materializer): HttpRequest = {
    val upgrade = new InternalCustomHeader("UpgradeToWebSocketTestHeader") with WebSocketUpgrade {
      def requestedProtocols: immutable.Seq[String] = subprotocols.toList

      def handleMessages(
          handlerFlow: Graph[FlowShape[Message, Message], Any],
          subprotocol: Option[String]
      ): HttpResponse = {
        clientSideHandler.join(handlerFlow).run()
        HttpResponse(
          StatusCodes.SwitchingProtocols,
          headers =
            Upgrade(UpgradeProtocol("websocket") :: Nil) ::
              subprotocol.map(p => `Sec-WebSocket-Protocol`(p :: Nil)).toList
        )
      }
    }
    val req = HttpRequest(uri = uri)
      .addAttribute(webSocketUpgrade, upgrade)
      .addHeader(upgrade)

    encoding.map(enc => req.addHeader(enc)).getOrElse(req)
  }
}
