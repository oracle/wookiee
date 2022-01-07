package com.oracle.infy.wookiee.grpc.contract

import cats.data.EitherT
import com.oracle.infy.wookiee.grpc.contract.StreamContract.StreamError

trait StreamContract[F[_], A, S[_[_], _]] {
  def stream: S[F, A]

  def evalN(n: Long): EitherT[F, StreamError, List[A]]

  def flatMap[B](f: A => StreamContract[F, B, S]): StreamContract[F, B, S]

}

object StreamContract {

  final case class StreamError(msg: String)

}
