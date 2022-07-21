package com.oracle.infy.wookiee.component.grpc.utils

import com.google.protobuf.StringValue
import com.typesafe.config.{Config, ConfigFactory}
import io.grpc.{BindableService, CallOptions, Channel, ServerServiceDefinition}
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.{AbstractStub, ServerCalls, StreamObserver}
import io.grpc.stub.ClientCalls.blockingUnaryCall
import io.grpc.stub.ServerCalls.asyncUnaryCall

object TestModels {
  def conf(zkPort: Int, grpcPort: Int): Config = ConfigFactory.parseString(s"""
       |wookiee-system {
       |  wookiee-zookeeper {
       |    quorum = "localhost:$zkPort"
       |  }
       |
       |  wookiee-grpc-component {
       |    grpc {
       |      port = $grpcPort
       |      zk-discovery-path = "/grpc/local_dev"
       |      server-host-name = "localhost"
       |      max-message-size = 8388608
       |    }
       |  }
       |}
       |""".stripMargin)

  trait GrpcMethodHolder {
    import io.grpc.MethodDescriptor

    protected def basicMethod(serviceName: String): MethodDescriptor[StringValue, StringValue] =
      MethodDescriptor
        .newBuilder[StringValue, StringValue]
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(s"$serviceName/serve")
        .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance))
        .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance))
        .build
  }

  class GrpcMockStub(channel: Channel) extends AbstractStub[GrpcMockStub](channel) with GrpcMethodHolder {

    override def build(channel: Channel, callOptions: CallOptions): GrpcMockStub = {
      new GrpcMockStub(channel)
    }

    def sayHello(request: StringValue, serviceName: String): StringValue =
      blockingUnaryCall(getChannel, basicMethod(serviceName), getCallOptions, request)
  }

  trait GrpcMockService extends BindableService with GrpcMethodHolder {

    override def bindService(): ServerServiceDefinition = {
      ServerServiceDefinition
        .builder(getClass.getSimpleName)
        .addMethod(
          basicMethod(serviceName),
          asyncUnaryCall(new ServerCalls.UnaryMethod[StringValue, StringValue]() {
            override def invoke(request: StringValue, responseObserver: StreamObserver[StringValue]): Unit = {
              println(s"Invoked [$serviceName] with request [${request.getValue}]")
              responseObserver.onNext(StringValue.of(s"${request.getValue}:$serviceName"))
              responseObserver.onCompleted()
            }
          })
        )
        .build
    }

    def serviceName: String = getClass.getSimpleName
  }

  class GrpcServiceOne extends GrpcMockService
  class GrpcServiceTwo extends GrpcMockService
  class GrpcServiceThree extends GrpcMockService
}
