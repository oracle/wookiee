package com.oracle.infy.wookiee.grpc.impl

import java.net.InetSocketAddress

import _root_.io.grpc.NameResolver.ResolutionResult
import cats.data.EitherT
import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Blocker, ContextShift, Fiber, IO}
import cats.implicits._
import com.oracle.infy.wookiee.grpc.contract.{HostnameServiceContract, ListenerContract}
import com.oracle.infy.wookiee.grpc.errors.Errors.WookieeGrpcError
import com.oracle.infy.wookiee.model.Host
import com.oracle.infy.wookiee.utils.implicits._
import fs2._
import io.chrisdavenport.log4cats.Logger
import io.grpc.{Attributes, EquivalentAddressGroup, NameResolver}

protected[grpc] class WookieeNameResolver(
    listenerRef: Ref[IO, Option[ListenerContract[IO, Stream]]],
    semaphore: Semaphore[IO],
    fiberRef: Ref[IO, Option[Fiber[IO, Either[WookieeGrpcError, Unit]]]],
    hostNameService: HostnameServiceContract[IO, Stream],
    discoveryPath: String
)(implicit cs: ContextShift[IO], blocker: Blocker, logger: Logger[IO])
    extends NameResolver {

  override def getServiceAuthority: String = {
    "zk"
  }

  override def shutdown(): Unit = {
    val computation = for {
      _ <- logger.info("Shutdown was called on NameResolver")
      maybeFiber <- fiberRef.get
      maybeListenerContract <- listenerRef.get
      _ <- maybeListenerContract match {
        case Some(listenerContract) => listenerContract.shutdown.value
        case None                   => ().asRight[WookieeGrpcError].pure[IO]
      }
      _ <- maybeFiber match {
        case Some(fiber) => fiber.cancel
        case None        => ().pure[IO]
      }
    } yield ()

    semaphore.acquire.bracket(_ => computation)(_ => semaphore.release).unsafeRunSync()
  }

  def listenerCallback(listener: NameResolver.Listener2): Set[Host] => IO[Unit] = { hosts =>
    IO {
      val addresses = hosts.map { host =>
        val attrBuilder = Attributes.newBuilder()
        attrBuilder.set(WookieeNameResolver.METADATA, host.metadata)
        new EquivalentAddressGroup(new InetSocketAddress(host.address, host.port), attrBuilder.build())
      }.toList

      listener.onResult(ResolutionResult.newBuilder().setAddresses(addresses.asJava).build())
    }
  }

  override def start(listener: NameResolver.Listener2): Unit = {

    val computation = for {
      _ <- logger.info("Start was called on NameResolver")
      wookieeListener <- new WookieeGrpcHostListener(listenerCallback(listener), hostNameService, discoveryPath)
        .pure[IO]

      _ <- listenerRef.set(Some(wookieeListener))

      fiber <- wookieeListener
        .startListening
        .leftFlatMap { err =>
          EitherT(logger.error("Error on listen start").map(_ => err.asLeft[Unit]))
        }
        .value
        .start
      _ <- fiberRef.set(Some(fiber))
      _ <- logger.info("Running listener in the background")
    } yield ()

    semaphore.acquire.bracket(_ => computation)(_ => semaphore.release).unsafeRunSync()
  }

  override def refresh(): Unit = {}
}

object WookieeNameResolver {
  protected[grpc] val METADATA: Attributes.Key[Map[String, String]] = Attributes.Key.create("metadata")

}
