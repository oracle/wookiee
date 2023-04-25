package com.oracle.infy.wookiee.component.discovery.command.grpc

import com.google.protobuf.StringValue
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.AbstractStub
import io.grpc.stub.ClientCalls.blockingUnaryCall
import io.grpc.{CallOptions, Channel, MethodDescriptor}

/**
  * The contents of this file are manually created, generic gRPC stubs for the DiscoverableCommand.
  * They are used as a client to execute a remote DiscoverableCommand. They take a JSON string (with formatter)
  * which the receiving server will deserialize to the DiscoverableCommand's Input type, and
  * then the command is executed.
  */
trait GrpcMethodHolder {

  // gRPC method for executing a remote command
  protected def basicMethod(commandName: String): MethodDescriptor[StringValue, StringValue] =
    MethodDescriptor
      .newBuilder[StringValue, StringValue]
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(s"$commandName/serve")
      .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance))
      .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance))
      .build
}

class GrpcDiscoverableStub(channel: Channel) extends AbstractStub[GrpcDiscoverableStub](channel) with GrpcMethodHolder {

  override def build(channel: Channel, callOptions: CallOptions): GrpcDiscoverableStub = {
    new GrpcDiscoverableStub(channel)
  }

  def executeRemote(request: StringValue, commandName: String): StringValue =
    blockingUnaryCall(getChannel, basicMethod(commandName), getCallOptions, request)
}
