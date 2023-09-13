package com.oracle.infy.wookiee.component.web.ws.tyrus

import org.glassfish.tyrus.core.TyrusWebSocketEngine
import org.glassfish.tyrus.server.TyrusServerContainer

import java.util
import javax.websocket.server.ServerEndpointConfig

// Based on the TyrusServerContainer implementation from io.helidon.webserver.tyrus.TyrusSupport
class WookieeTyrusContainer extends TyrusServerContainer(util.Set.of[Class[_]]) {
  private val engine: TyrusWebSocketEngine = TyrusWebSocketEngine.builder(this).build

  def register(serverEndpointConfig: ServerEndpointConfig, contextPath: String): Unit =
    engine.register(serverEndpointConfig, contextPath)

  override def register(endpointClass: Class[_]): Unit =
    throw new UnsupportedOperationException("Use WookieeTyrusContainer.register(class, path) for registration")

  override def register(serverEndpointConfig: ServerEndpointConfig): Unit =
    throw new UnsupportedOperationException("Use WookieeTyrusContainer.register(config, path) for registration")

  override def getWebSocketEngine: TyrusWebSocketEngine =
    engine
}
