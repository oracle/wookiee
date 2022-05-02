package com.oracle.infy.wookiee.grpc.tests

import cats.data.EitherT
import cats.effect.std.Queue
import cats.effect.{Concurrent, Deferred, IO}
import cats.implicits.{catsSyntaxEq => _, _}
import com.oracle.infy.wookiee.grpc.common.{HostGenerator, UTestScalaCheck}
import com.oracle.infy.wookiee.grpc.contract.ListenerContract
import com.oracle.infy.wookiee.grpc.errors.Errors.{UnknownWookieeGrpcError, WookieeGrpcError}
import com.oracle.infy.wookiee.grpc.model.Host
import com.oracle.infy.wookiee.grpc.utils.implicits.{MultiversalEquality, ToEitherT}
import fs2._
import org.scalacheck.Prop
import org.scalacheck.Prop.forAll
import utest.{Tests, test}

object GrpcListenerTest extends UTestScalaCheck with HostGenerator {

  def tests(
      minSuccessfulRuns: Int,
      factory: (Set[Host] => IO[Unit]) => IO[(Set[Host] => IO[Unit], () => IO[Unit], ListenerContract[IO, Stream])]
  )(implicit fToProp: EitherT[IO, WookieeGrpcError, Boolean] => Prop): Tests = {

    val callbackIsInvokedAfterStartListening = {
      forAll { hosts: Set[Host] =>
        for {

          queue <- Queue
            .unbounded[IO, Set[Host]]
            .toEitherT(t => (UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError))

          c <- factory(hostsFromHostStream => {
            queue.offer(hostsFromHostStream)
          }).toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          (sendHosts, cleanup, listener) = c

          _ <- sendHosts(hosts)
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          bgRef <- listener
            .startListening
            .value
            .start
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          result <- Stream
            .repeatEval(queue.take)
            .collectFirst {
              case hostsFromQueue if hostsFromQueue.size === hosts.size => hostsFromQueue
            }
            .compile
            .toList
            .map {
              case head :: Nil => head === hosts
              case _           => false
            }
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          _ <- listener.shutdown
          _ <- bgRef
            .join
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)
          _ <- cleanup()
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)
        } yield result
      }
    }

    val shutdownIsIdempotent = {
      forAll { hosts: Set[Host] =>
        for {
          promise <- Deferred[IO, Unit]
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          c <- factory(_ => promise.complete(()).as(()))
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          (sendHosts, cleanup, listener) = c
          bgRef <- listener
            .startListening
            .value
            .start
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          _ <- sendHosts(hosts)
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          _ <- promise
            .get
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          _ <- listener.shutdown
          result <- true
            .pure[IO]
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          _ <- bgRef
            .join
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

          _ <- cleanup()
            .toEitherT(t => UnknownWookieeGrpcError(t.getMessage): WookieeGrpcError)

        } yield result
      }
    }

    Tests {
      test("call back is invoked after start listening") {
        callbackIsInvokedAfterStartListening.checkUTest(minSuccessfulRuns)
      }

      test("Shutdown is idempotent") {
        shutdownIsIdempotent.checkUTest(minSuccessfulRuns)
      }
    }
  }

}
