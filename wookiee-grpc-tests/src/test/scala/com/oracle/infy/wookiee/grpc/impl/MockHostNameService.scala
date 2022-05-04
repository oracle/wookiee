package wookiee.grpc.impl

import cats.data.EitherT
import cats.effect.{Concurrent, IO}
import cats.implicits._
import com.oracle.infy.wookiee.grpc.contract._
import com.oracle.infy.wookiee.grpc.errors.Errors._
import com.oracle.infy.wookiee.grpc.model.Host
import fs2._

class MockHostNameService(stream: CloseableStreamContract[IO, Set[Host], Stream])(implicit c: Concurrent[IO])
    extends HostnameServiceContract[IO, Stream] {

  override def shutdown: EitherT[IO, WookieeGrpcError, Unit] =
    stream.shutdown().leftMap(err => HostNameServiceError(err.msg))

  override def hostStream(
      serviceId: String
  ): EitherT[IO, WookieeGrpcError, CloseableStreamContract[IO, Set[Host], Stream]] =
    EitherT(stream.asRight[WookieeGrpcError].pure[IO])
}
