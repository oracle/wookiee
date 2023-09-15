package com.oracle.infy.wookiee.discovery.command.grpc

import com.google.protobuf.StringValue
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommand
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ClassUtil
import io.grpc.Status.Code
import io.grpc.stub.ServerCalls.asyncUnaryCall
import io.grpc.stub.{ServerCalls, StreamObserver}
import io.grpc.{BindableService, ServerServiceDefinition, Status, StatusException, StatusRuntimeException}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
  * The contents of this file are manually created, generic gRPC bindable services for the DiscoverableCommand.
  * They are used to create flexible gRPC endpoints that can take a JSON string (with formatter)
  * which is deserialized to the DiscoverableCommand's Input type, and then the command is executed.
  * The result is then sent back as JSON which will be deserialized by the client.
  */
class GrpcJITService[Input <: Any: ClassTag](command: DiscoverableCommand[Input, _ <: Any])(
    implicit val ec: ExecutionContext
) extends BindableService
    with LoggingAdapter
    with GrpcMethodHolder {

  override def bindService(): ServerServiceDefinition = {
    // Register the command indexed by its commandName
    ServerServiceDefinition
      .builder(command.commandName)
      .addMethod(
        basicMethod(command.commandName),
        asyncUnaryCall(new ServerCalls.UnaryMethod[StringValue, StringValue]() {

          override def invoke(request: StringValue, responseObserver: StreamObserver[StringValue]): Unit = {
            def respondWithError(ex: Throwable, message: String): Unit = {
              ex match {
                case _: StatusException        => responseObserver.onError(ex)
                case _: StatusRuntimeException => responseObserver.onError(ex)
                case _ =>
                  responseObserver.onError(
                    new StatusException(Status.INTERNAL.withCause(ex).withDescription(s"$message: '${ex.getMessage}'"))
                  )
              }
            }

            try {
              // Take the StringValue from gRPC and convert it to the input type of the command
              log.debug(s"GJIT100: Invoked [${command.commandName}] with request [${request.getValue}]")
              val input = command.jsonToInput(request.getValue)
              // Invoke the command
              command.execute(input) onComplete {
                case Success(output) =>
                  log
                    .debug(s"GJIT101: Successfully invoked [${command.commandName}] with request [${request.getValue}]")
                  // Take the output of the command and convert it to a StringValue for gRPC
                  val asString = ClassUtil.writeAny(output)(command.format)
                  responseObserver.onNext(StringValue.of(asString))
                  responseObserver.onCompleted()
                case Failure(ex) =>
                  // Issue happened on the server side
                  log.error(
                    s"GJIT102: Errant execution of command [${command.commandName}] with request [${request.getValue}]",
                    ex
                  )
                  respondWithError(ex, "Failed gRPC execution")
              }
            } catch {
              case ex: Throwable =>
                // Issue happened on the client side
                log.error(
                  s"GJIT103: Error invoking command [${command.commandName}] with request [${request.getValue}]",
                  ex
                )
                respondWithError(ex, "Failed gRPC invocation")
            }
          }
        })
      )
      .build
  }

}
