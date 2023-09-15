package com.oracle.infy.wookiee.component.grpc.utils

import com.google.protobuf.StringValue
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.typesafe.config.{Config, ConfigFactory}
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.ClientCalls.blockingUnaryCall
import io.grpc.stub.ServerCalls.asyncUnaryCall
import io.grpc.stub.{AbstractStub, ServerCalls, StreamObserver}
import io.grpc.{BindableService, CallOptions, Channel, ServerServiceDefinition}

object TestModels extends LoggingAdapter {
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
       |      max-message-size = 10000000
       |      retry-delay-sec = 3
       |      zk-retry-period-sec = 3
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
              val reqValue = request.getValue
              log.info(
                s"Invoked [$serviceName] with request [${reqValue.substring(0, Math.min(2000, reqValue.length))}]"
              )
              responseObserver.onNext(StringValue.of(s"$reqValue:$serviceName"))
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
  class GrpcServiceFour extends GrpcMockService
}
