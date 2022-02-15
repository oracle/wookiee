package com.oracle.infy.wookiee.grpc.contract

import cats.data.EitherT
import com.oracle.infy.wookiee.grpc.errors.Errors.WookieeGrpcError
import com.oracle.infy.wookiee.grpc.model.Host

trait HostnameServiceContract[F[_], S[_[_], _]] {
  def shutdown: EitherT[F, WookieeGrpcError, Unit]

  def hostStream(serviceId: String): EitherT[F, WookieeGrpcError, CloseableStreamContract[F, Set[Host], S]]
}
