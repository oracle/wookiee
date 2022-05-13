package com.oracle.infy.wookiee.grpc.impl

import io.grpc._

import java.util.concurrent.atomic.AtomicReference

object TestInterceptors {
  val serverInterceptorHit: AtomicReference[Boolean] = new AtomicReference[Boolean](false)
  val clientInterceptorHit: AtomicReference[Boolean] = new AtomicReference[Boolean](false)

  class TestServerInterceptor extends ServerInterceptor {

    def interceptCall[ReqT, RespT](
        serverCall: ServerCall[ReqT, RespT],
        metadata: Metadata,
        serverCallHandler: ServerCallHandler[ReqT, RespT]
    ): ServerCall.Listener[ReqT] = {
      serverInterceptorHit.set(true)
      serverCallHandler.startCall(serverCall, metadata)
    }
  }

  class TestClientInterceptor extends ClientInterceptor {

    def interceptCall[ReqT, RespT](
        methodDescriptor: MethodDescriptor[ReqT, RespT],
        callOptions: CallOptions,
        channel: Channel
    ): ClientCall[ReqT, RespT] =
      new ForwardingClientCall.SimpleForwardingClientCall[ReqT, RespT](channel.newCall(methodDescriptor, callOptions)) {

        override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit = {
          clientInterceptorHit.set(true)
          super.start(responseListener, headers)
        }
      }
  }
}
