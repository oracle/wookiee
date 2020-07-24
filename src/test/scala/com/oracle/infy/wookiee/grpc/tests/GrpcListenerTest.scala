package com.oracle.infy.wookiee.grpc.tests

import cats.Monad
import cats.data.EitherT
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, Sync}
import cats.implicits.{catsSyntaxEq => _, _}
import com.oracle.infy.wookiee.grpc.common.{HostGenerator, UTestScalaCheck}
import com.oracle.infy.wookiee.grpc.contract.ListenerContract
import com.oracle.infy.wookiee.grpc.errors.Errors.{UnknownWookieeGrpcError, WookieeGrpcError}
import com.oracle.infy.wookiee.model.Host
import com.oracle.infy.wookiee.utils.implicits
import com.oracle.infy.wookiee.utils.implicits._
import fs2.concurrent.Queue
import org.scalacheck.Prop
import org.scalacheck.Prop.forAll
import utest.{Tests, test}

object GrpcListenerTest extends UTestScalaCheck with HostGenerator {

  private implicit class ToEitherT[A, F[_]: Monad: Sync](lhs: F[A]) {

    def toEitherT: EitherT[F, WookieeGrpcError, A] = {
      implicits
        .ToEitherT(lhs)
        .toEitherT(e => UnknownWookieeGrpcError(e.getMessage))
    }
  }

  def tests[F[_]: Monad: Sync: Concurrent, S[_[_], _]](
      factory: (Set[Host] => F[Unit]) => F[(Set[Host] => F[Unit], () => F[Unit], ListenerContract[F, S])]
  )(implicit fToProp: EitherT[F, WookieeGrpcError, Boolean] => Prop): Tests = {

    val callbackIsInvokedAfterStartListening = {
      forAll { hosts: Set[Host] =>
        for {
          queue <- Queue.unbounded[F, Set[Host]].toEitherT
          c <- factory(hostsFromHostStream => {
            queue.enqueue1(hostsFromHostStream)
          }).toEitherT
          (sendHosts, cleanup, listener) = c
          _ <- sendHosts(hosts).toEitherT
          bgRef <- Concurrent[F].start(listener.startListening.value).toEitherT
          result <- queue
            .dequeue
            .collectFirst {
              case hostsFromQueue if hostsFromQueue.size === hosts.size => hostsFromQueue
            }
            .compile
            .toList
            .map {
              case head :: Nil => head === hosts
              case _           => false
            }
            .toEitherT
          _ <- listener.shutdown
          _ <- bgRef.join.toEitherT
          _ <- cleanup().toEitherT
        } yield result
      }
    }

    val shutdownIsIdempotent = {
      forAll { hosts: Set[Host] =>
        for {
          promise <- Deferred[F, Unit].toEitherT
          c <- factory(_ => promise.complete(())).toEitherT
          (sendHosts, cleanup, listener) = c
          bgRef <- Concurrent[F].start(listener.startListening.value).toEitherT
          _ <- sendHosts(hosts).toEitherT
          _ <- promise.get.toEitherT
          _ <- listener.shutdown
          result <- true.pure[F].toEitherT
          _ <- bgRef.join.toEitherT
          _ <- cleanup().toEitherT
        } yield result
      }
    }

    Tests {
      test("call back is invoked after start listening") {
        callbackIsInvokedAfterStartListening.checkUTest()
      }

      test("Shutdown is idempotent") {
        shutdownIsIdempotent.checkUTest()
      }
    }
  }

}
