package com.oracle.infy.wookiee.grpc.impl

import com.oracle.infy.wookiee.grpc.settings.ClientAuthSettings
import io.grpc.{CallOptions, Channel, ClientCall, ClientInterceptor, ForwardingClientCall, Metadata, MethodDescriptor}
import io.grpc.Metadata.Key

class BearerTokenClientProvider(clientAuthSettings: ClientAuthSettings) extends ClientInterceptor {
  private final val AUTHORIZATION_HEADER = "Authorization"
  private final val BEARER_PREFIX = "Bearer "

  def interceptCall[ReqT, RespT](methodDescriptor: MethodDescriptor[ReqT, RespT], callOptions: CallOptions, channel: Channel): ClientCall[ReqT, RespT] = {
    new ForwardingClientCall.SimpleForwardingClientCall[ReqT, RespT](channel.newCall(methodDescriptor, callOptions)) {

      override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit = {
        headers.put(Key.of(AUTHORIZATION_HEADER, Metadata.ASCII_STRING_MARSHALLER), BEARER_PREFIX + clientAuthSettings.token)
        super.start(responseListener, headers)
      }
    }
  }
}

object BearerTokenClientProvider {
  def apply(clientAuthSettings: ClientAuthSettings): BearerTokenClientProvider = {
    new BearerTokenClientProvider(clientAuthSettings)
  }
}