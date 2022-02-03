package com.oracle.infy.wookiee.grpc.contract

import cats.data.EitherT
import com.oracle.infy.wookiee.grpc.contract.StreamContract.StreamError

trait CloseableStreamContract[F[_], A, S[_[_], _]] extends StreamContract[F, A, S] {

  def shutdown(): EitherT[F, StreamError, Unit]
}
