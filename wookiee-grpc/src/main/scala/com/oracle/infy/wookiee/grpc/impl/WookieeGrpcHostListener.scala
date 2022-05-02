package com.oracle.infy.wookiee.grpc.impl

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.oracle.infy.wookiee.grpc.contract._
import com.oracle.infy.wookiee.grpc.errors.Errors.{ListenerError, WookieeGrpcError}
import com.oracle.infy.wookiee.grpc.model.Host
import fs2._
import org.typelevel.log4cats.Logger

protected[grpc] class WookieeGrpcHostListener(
    listenerCallback: Set[Host] => IO[Unit],
    hostnameServiceContract: HostnameServiceContract[IO, Stream],
    discoveryPath: String
)(implicit logger: Logger[IO])
    extends ListenerContract[IO, Stream](hostnameServiceContract) {

  override def startListening: EitherT[IO, WookieeGrpcError, Unit] =
    for {
      closableStream <- hostnameServiceContract.hostStream(discoveryPath)
      r <- EitherT(
        closableStream
          .stream
          .evalTap(hosts => logger.info(s"Got hosts $hosts on stream"))
          .evalTap(hosts => listenerCallback(hosts))
          .compile
          .drain
          .map(_.asRight[ListenerError])
          .handleErrorWith(err => (ListenerError(err.getMessage): WookieeGrpcError).asLeft[Unit].pure[IO])
      )
    } yield r

}
