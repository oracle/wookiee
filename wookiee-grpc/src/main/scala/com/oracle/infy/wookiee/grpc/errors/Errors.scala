package com.oracle.infy.wookiee.grpc.errors

object Errors {

  sealed trait WookieeGrpcError
  case class HostNameServiceError(msg: String) extends WookieeGrpcError
  case class ListenerError(msg: String) extends WookieeGrpcError

  final case class UnknownWookieeGrpcError(err: String) extends WookieeGrpcError
  final case class UnknownCuratorShutdownError(err: String) extends WookieeGrpcError
  final case class UnknownShutdownError(err: String) extends WookieeGrpcError
  final case class UnknownHostStreamError(err: String) extends WookieeGrpcError
  final case class FailedToStartGrpcServerError(err: String) extends WookieeGrpcError
}
