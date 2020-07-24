package com.oracle.infy.wookiee.grpc.impl

import cats.data.EitherT
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, IO}
import cats.implicits._
import com.oracle.infy.wookiee.grpc.contract.StreamContract.StreamError
import com.oracle.infy.wookiee.grpc.contract.{CloseableStreamContract, StreamContract}
import fs2.Stream
import com.oracle.infy.wookiee.utils.implicits._

protected[grpc] final case class Fs2CloseableImpl[T](
    strm: Stream[IO, T],
    interruptSignal: Deferred[IO, Either[Throwable, Unit]]
)(
    implicit c: Concurrent[IO]
) extends CloseableStreamContract[IO, T, Stream] {

  override val stream: Stream[IO, T] = strm.interruptWhen(interruptSignal.get)

  override def evalN(n: Long): EitherT[IO, StreamContract.StreamError, List[T]] = {
    EitherT(
      stream
        .take(n)
        .compile
        .toList
        .map(_.asRight[StreamError])
        .handleErrorWith { err =>
          StreamError(err.getMessage).asLeft[List[T]].pure[IO]
        }
    )
  }

  override def flatMap[B](f: T => StreamContract[IO, B, Stream]): StreamContract[IO, B, Stream] = {
    Fs2CloseableImpl(stream.flatMap(f(_).stream), interruptSignal)
  }

  override def shutdown(): EitherT[IO, StreamError, Unit] = {
    interruptSignal
      .complete(().asRight[Throwable])
      .attempt
      .void
      .toEitherT(err => StreamError(err.getMessage))
  }
}
