package com.oracle.infy.wookiee.grpc.impl

import com.oracle.infy.wookiee.grpc.settings.ServiceAuthSettings
import io.grpc.Metadata.Key
import io.grpc._

class BearerTokenAuthenticator(authSettings: ServiceAuthSettings) extends ServerInterceptor {
  private val AUTHORIZATION_HEADER = "Authorization"
  private val BEARER_PREFIX = "Bearer "

  def interceptCall[ReqT, RespT](
      serverCall: ServerCall[ReqT, RespT],
      metadata: Metadata,
      serverCallHandler: ServerCallHandler[ReqT, RespT]
  ): ServerCall.Listener[ReqT] = {
    val authenticated = Option(metadata.get(Key.of(AUTHORIZATION_HEADER, Metadata.ASCII_STRING_MARSHALLER)))
      .map(_.replace(BEARER_PREFIX, "").trim)
      .contains(authSettings.token.trim)

    if (!authenticated) {
      serverCall.close(Status.UNAUTHENTICATED, new Metadata)
    }

    serverCallHandler.startCall(serverCall, metadata)
  }

}

object BearerTokenAuthenticator {

  def apply(authSettings: ServiceAuthSettings): BearerTokenAuthenticator =
    new BearerTokenAuthenticator(authSettings)
}
