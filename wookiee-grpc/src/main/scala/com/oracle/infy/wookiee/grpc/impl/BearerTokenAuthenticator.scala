package com.oracle.infy.wookiee.grpc.impl

import com.oracle.infy.wookiee.grpc.settings.ServiceAuthSettings
import io.grpc.ServerInterceptor
import io.grpc.Metadata
import io.grpc.Metadata.Key
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.Status
import io.grpc.StatusRuntimeException

class BearerTokenAuthenticator(authSettings: ServiceAuthSettings) extends ServerInterceptor {
  private final val AUTHORIZATION_HEADER = "Authorization"
  private final val BEARER_PREFIX = "Bearer "
  def interceptCall[ReqT, RespT](serverCall: ServerCall[ReqT, RespT], metadata: Metadata, serverCallHandler: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
    val auth_token = metadata.get(Key.of(AUTHORIZATION_HEADER, Metadata.ASCII_STRING_MARSHALLER))
    if (auth_token.nonEmpty || !(auth_token.replace(BEARER_PREFIX, "").trim == authSettings.token.trim)) throw new StatusRuntimeException(Status.FAILED_PRECONDITION)
    serverCallHandler.startCall(serverCall, metadata)
  }

}

object BearerTokenAuthenticator {
  def apply(authSettings: ServiceAuthSettings): BearerTokenAuthenticator = {
    new BearerTokenAuthenticator(authSettings)
  }
}