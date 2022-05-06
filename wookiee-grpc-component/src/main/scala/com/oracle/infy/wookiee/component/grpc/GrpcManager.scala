package com.oracle.infy.wookiee.component.grpc

import cats.data.EitherT
import cats.effect.{Blocker, ContextShift, IO, Timer}
import com.oracle.infy.wookiee.component.Component
import com.oracle.infy.wookiee.component.grpc.GrpcManager._
import com.oracle.infy.wookiee.grpc.WookieeGrpcServer
import com.oracle.infy.wookiee.grpc.settings.ServiceAuthSettings
import com.oracle.infy.wookiee.grpc.utils.implicits.ToEitherT
import io.grpc.{ServerServiceDefinition}
import org.typelevel.log4cats.Logger

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object GrpcManager {

  case class GrpcDefinitions(list: List[(ServerServiceDefinition, Option[ServiceAuthSettings])])

  case class GrpcDefinitionsAck()

  case class GrpcServiceDefinition(name: String, services: GrpcDefinitions)

  case class InitializeServers()

  case class CleanCheck()

  case class CleanResponse(clean: Boolean)
}


class GrpcManager(name: String) extends Component(name) {

  private val server: AtomicReference[Option[WookieeGrpcServer]] = new AtomicReference(None)

  override def preStart(): Unit = {
    super.preStart()

    log.info(s"GRPCM100: Starting up CDR Extension gRPC Manager at path [${self.path}]")
    context.become(clean(Map(), None))
  }

  override def postStop(): Unit = {
    super.postStop()

    log.info(s"GRPCM400: Stopping the CDR gRPC Manager and shutting down server..")
    server.get().foreach(_.awaitTermination().unsafeRunAsyncAndForget())
  }

  // We will usually be in this state, until we get a GrpcServiceDefinition call which sends us into the dirty state
  def clean(
             defs: Map[String, List[(ServerServiceDefinition, Option[ServiceAuthSettings])]],
             server: Option[WookieeGrpcServer]
           ): Receive =
    super.receive orElse {
      case GrpcServiceDefinition(name, services) =>
        import context.dispatcher

        log.info(s"GRPCM101: Entity [$name] wants to load ${services.list.size} gRPC service definitions..")
        context.become(dirty(defs.updated(name, services.list), server))
        context.system.scheduler.scheduleOnce(10.seconds, self, InitializeServers())
        sender() ! GrpcDefinitionsAck()

      case _: InitializeServers => // Do nothing, we've already started gRPC server for all registered defs
      case _: CleanCheck => sender() ! CleanResponse(server.isDefined)
    }

  // From this state we will wait 5 seconds (to let other definitions roll in) then we'll get an
  // InitializeServers call which will trigger the start of the gRPC server
  def dirty(
             defs: Map[String, List[(ServerServiceDefinition, Option[ServiceAuthSettings])]],
             server: Option[WookieeGrpcServer]
           ): Receive =
    super.receive orElse {
      case GrpcServiceDefinition(name, services) =>
        log.info(s"GRPCM102: Entity [$name] wants to load ${services.list.size} gRPC service definitions..")
        context.become(dirty(defs.updated(name, services.list), server))
        sender() ! GrpcDefinitionsAck()

      case _: InitializeServers =>
        log.info(s"GRPCM103: Got message to start gRPC for ${defs.values.flatten.size} gRPC service definitions..")
        val newServer = startGrpc(defs, server)
        if (newServer.isDefined)
          log.info(s"GRPCM104: Successfully started gRPC Manager's services")
        else
          log.warn(s"GRPCM105: Failed to start gRPC Manager's services, check logs above")

        this.server.set(newServer)
        context.become(clean(defs, newServer))

      case _: CleanCheck => sender() ! CleanResponse(false)
    }

  private def shutdownCurrent(currentServer: Option[WookieeGrpcServer]): EitherT[IO, CdrError, Unit] = {
    (currentServer match {
      case Some(server) => server.shutdown()
      case _ => IO.unit
    }).toEitherT
  }

  private def startGrpc(
                         definitions: Map[String, List[(ServerServiceDefinition, Option[ServiceAuthSettings])]],
                         currentServer: Option[WookieeGrpcServer]
                       ): Option[WookieeGrpcServer] = try {
    implicit val configSource: ConfigObjectSource = ConfigSource.fromConfig(config)
    implicit val cs: ContextShift[IO] = IO.contextShift(context.dispatcher)

    (for {
      implicit0(config: CdrConfig) <- parseConfig(configSource)
      implicit0(blocker: Blocker) <- IO(createEC("grpc-manager-blocking")).toEitherT.map(Blocker.liftExecutionContext)
      implicit0(logger: Logger[IO]) <- appLogger
      scheduledExecutor <- scheduledThreadPoolExecutor("grpc-manager")
      dispatcherExecutorContext <- IO(createEC("grpc-manager-dispatcher")).toEitherT
      implicit0(mainEC: ExecutionContext) <- IO(createEC("grpc-manager-main")).toEitherT

      implicit0(timer: Timer[IO]) = IO.timer(dispatcherExecutorContext, scheduledExecutor)
      defs = definitions.values.flatten.toList
      // Shutdown existing server if present
      _ <- shutdownCurrent(currentServer)
      server <-
        defs.headOption match {
          case Some(first) =>
            val someStart = startGrpcServers(NonEmptyList(first, defs.drop(1)), config)
              .map(srv => Some(srv): Option[WookieeGrpcServer])
            someStart.attemptT.leftMap(t => FailedToStartGrpcServerError(t.getMessage, t): CdrError)
          case None =>
            log.info("GRPCM203: No Service Definitions Provided, Not Starting Server")
            (None: Option[WookieeGrpcServer]).asRight[CdrError].toEitherT[IO]
        }
    } yield server)
      .value
      .unsafeRunSync() match {
      case Left(err) =>
        err match {
          case confErr: UnableToParseConfigError =>
            writeError(s"GRPCM204: Unable to load config when starting gRPC: '${confErr.msg}'")
          case startFail: FailedToStartGrpcServerError =>
            writeError(s"GRPCM205: Unable to start gRPC: '${startFail.msg}'", Some(startFail.err))
          case cdrErr: CdrError =>
            writeError(s"GRPCM206: Unknown error when starting gRPC: '$cdrErr'")
        }

      case Right(server) => server
    }
  } catch {
    case initializerError: ExceptionInInitializerError =>
      writeError(s"GRPCM210: Failed to start gRPC Service due to below error", Some(initializerError))
    case ex: Throwable =>
      writeError(s"GRPCM211: Unexpected error when starting gRPC Service", Some(ex))
  }

  private def writeError(msg: String): Option[WookieeGrpcServer] = writeError(msg, None)

  private def writeError(msg: String, t: Option[Throwable]): Option[WookieeGrpcServer] = {
    t match {
      case Some(err) => log.error(msg, err)
      case None => log.error(msg)
    }
    None
  }
}
