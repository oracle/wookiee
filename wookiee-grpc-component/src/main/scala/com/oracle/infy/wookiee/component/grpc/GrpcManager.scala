package com.oracle.infy.wookiee.component.grpc

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.pattern.ask
import akka.util.Timeout
import cats.data.{EitherT, NonEmptyList}
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.implicits._
import com.oracle.infy.wookiee.component.Component
import com.oracle.infy.wookiee.component.grpc.server.GrpcServer
import com.oracle.infy.wookiee.grpc.errors.Errors._
import com.oracle.infy.wookiee.grpc.model.LoadBalancers.RoundRobinPolicy
import com.oracle.infy.wookiee.grpc.settings.{
  ChannelSettings,
  ClientAuthSettings,
  SSLClientSettings,
  ServiceAuthSettings
}
import com.oracle.infy.wookiee.grpc.utils.implicits.{Java2ScalaConverterList, _}
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer, WookieeGrpcUtils}
import com.oracle.infy.wookiee.logging.LoggingAdapter
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config
import io.grpc.{ServerInterceptor, ServerServiceDefinition}
import org.apache.curator.framework.CuratorFramework
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.atomic.AtomicReference
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object GrpcManager extends LoggingAdapter {
  val ComponentName = "wookiee-grpc-component"

  // Actor of type GrpcManager
  // @throws IllegalStateException if manager has not been registered
  def getGrpcManager(system: ActorSystem): ActorRef = getMediator(system)

  /**
    * Convenience method to register a set of GrpcDefinitions, they will be added to the services already registered
    * @param system Main Wookiee Actor System, should be accessible as system, context.system, or actorSystem in any Actor
    * @param groupName Name of this group of gRPC services, the value is arbitrary but should be unique for each list of definitions on a server
    * @param defs The list of GrpcDefinition objects we'll use to know what to register
    * @throws IllegalStateException If manager actor hasn't started yet, consider calling waitForManager(system) if this happens
    */
  def registerGrpcService(system: ActorSystem, groupName: String, defs: java.util.List[GrpcDefinition]): Unit = {
    registerGrpcService(system, groupName, defs.asScala)
  }

  def registerGrpcService(system: ActorSystem, groupName: String, defs: List[GrpcDefinition]): Unit = {
    val manager = getGrpcManager(system)
    manager ! GrpcServiceDefinition(groupName, defs.asJava)
  }

  def waitForManager(system: ActorSystem): Unit =
    waitForManager(system, waitForClean = false)

  // Call this to ensure that gRPC Manager has finished registering gRPC services, useful for testing
  // Only use 'waitForClean = true' if you've already called registerGrpcService(..) and want to wait for gRPC to come up
  def waitForManager(system: ActorSystem, waitForClean: Boolean): Unit = {
    println("Waiting for grpc manager to report clean")
    implicit val mainEC: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(15.seconds)

    def cleanCheck(): Boolean = {
      grpcManagerMap.get(system) match {
        case Some(grpcManagerActor) =>
          val resp = Await.result((grpcManagerActor ? CleanCheck()).mapTo[CleanResponse], 15.seconds)
          if (waitForClean) resp.clean else true
        case None => false
      }
    }

    val cleanWaiter = Future {
      while (!cleanCheck()) {} // scalafix:ok
    }
    Await.result(cleanWaiter, 20.seconds)
    log.info("Grpc Manager is clean and ready to go")
  }

  def createChannel(zkPath: String, zkConnect: String, bearerToken: String): WookieeGrpcChannel =
    createChannel(zkPath, zkConnect, bearerToken, None)

  /**
    * Useful method for creating a gRPC channel to an existing gRPC service,
    * such as the ones GrpcManager is capable of starting
    *
    * @param zkPath Config at 'wookiee-grpc-component.grpc.zk-discovery-path'
    * @param zkConnect Zookeeper connect string, e.g. 'localhost:2121'
    * @param bearerToken Optional bearer token if one is setup on the server, null or empty string otherwise
    * @param sslClientSettings Optional settings for certs, TLS, and service authority if needed by target
    * @return A WookieeGrpcChannel that has a field .managedChannel() that can be put into stubs
    */
  def createChannel(
      zkPath: String,
      zkConnect: String,
      bearerToken: String,
      sslClientSettings: Option[SSLClientSettings]
  ): WookieeGrpcChannel = {
    import cats.effect.unsafe.implicits.global

    val ec: ExecutionContext = ThreadUtil.createEC(s"grpc-channel-${System.currentTimeMillis()}")
    val blockingEC: ExecutionContext = ThreadUtil.createEC(s"grpc-blocking-${System.currentTimeMillis()}")
    implicit val dispatcher: Dispatcher[IO] = ThreadUtil.dispatcherIO()

    def channelSettings(curatorFramework: CuratorFramework) = ChannelSettings(
      serviceDiscoveryPath = zkPath,
      eventLoopGroupExecutionContext = blockingEC,
      channelExecutionContext = ec,
      offloadExecutionContext = blockingEC,
      eventLoopGroupExecutionContextThreads = 4,
      lbPolicy = RoundRobinPolicy,
      curatorFramework = curatorFramework,
      sslClientSettings = sslClientSettings,
      clientAuthSettings = Option(bearerToken).filter(_.nonEmpty).map(ClientAuthSettings.apply)
    )

    (for {
      implicit0(logger: Logger[IO]) <- Slf4jLogger.create[IO]
      curator <- WookieeGrpcUtils
        .createCurator(
          zkConnect,
          5.seconds,
          blockingEC
        )
      _ <- IO(curator.start())
      channel <- WookieeGrpcChannel.of(channelSettings(curator))
    } yield {
      channel
    }).unsafeRunSync()
  }

  private val grpcManagerMap = TrieMap[ActorSystem, ActorRef]()

  private[wookiee] def getMediator(system: ActorSystem): ActorRef = {
    grpcManagerMap.get(system) match {
      case Some(zkActor) => zkActor
      case None =>
        throw new IllegalStateException(s"No gRPC Manager Registered for System: [$system]") //scalafix:ok
    }
  }

  private[wookiee] def registerMediator(actor: ActorRef)(implicit system: ActorSystem) = {
    log.info(s"Registering gRPC Manager: [${actor.path}], for actor system: [$system]")
    grpcManagerMap.put(system, actor)
  }

  private[wookiee] def unregisterMediator(system: ActorSystem): Unit = {
    if (grpcManagerMap.contains(system)) {
      log.info(s"Unregistering gRPC Manager for actor system: [$system]")
      grpcManagerMap.remove(system) foreach (_ ! PoisonPill)
      ()
    }
  }

  class GrpcDefinition(
      val definition: ServerServiceDefinition,
      val authSettings: Option[ServiceAuthSettings] = None,
      val interceptors: Option[List[ServerInterceptor]] = None
  ) {
    def this(definition: ServerServiceDefinition) =
      this(definition, None, None)

    // Won't break if authSettings == null
    def this(definition: ServerServiceDefinition, authSettings: ServiceAuthSettings) =
      this(definition, Option(authSettings), None)

    // Won't break if authSettings or interceptors == null
    def this(
        definition: ServerServiceDefinition,
        authSettings: ServiceAuthSettings,
        intercepts: java.util.List[ServerInterceptor]
    ) = {
      this(definition, Option(authSettings), Option(intercepts).map(_.asScala))
    }
  }

  case class GrpcDefinitionsAck()

  case class GrpcServiceDefinition(name: String, services: java.util.List[GrpcDefinition])

  case class InitializeServers()

  case class CleanCheck()

  case class CleanResponse(clean: Boolean)
}

/** This Component is meant to manage the single gRPC server endpoint that will host all of the
  * GrpcServiceDefinition objects passed to it by any number of entities in the Service. It does nothing on
  * its own but will be activated when it receives a GrpcServiceDefinition from anywhere. After getting that
  * message it will wait 10 seconds to allow others to register, then it will attempt to start the gRPC server--
  * shutting down any existing instance of it along the way (so late registrations are OK).
  *
  * Note for maintainers: Everything in this class is synchronous and that is taken for granted in the design,
  * any additional functionality should be similarly blocking on each message that comes in.
  */
class GrpcManager(name: String) extends Component(name) with GrpcServer {
  import GrpcManager._

  private val server: AtomicReference[Option[WookieeGrpcServer]] = new AtomicReference(None)

  override def preStart(): Unit = {
    super.preStart()

    log.info(s"WGM100: Starting up CDR Extension gRPC Manager at path [${self.path}]")
    // Register this actor so others can access it
    GrpcManager.registerMediator(self)
    context.become(clean(Map(), None))
  }

  override def postStop(): Unit = {
    super.postStop()

    log.info(s"WGM400: Stopping the CDR gRPC Manager and shutting down server..")
    GrpcManager.unregisterMediator(actorSystem)
    server.get().foreach(_.shutdown().unsafeRunAndForget())
  }

  // We will usually be in this state, until we get a GrpcServiceDefinition call which sends us into the dirty state
  def clean(
      defs: Map[String, List[(ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])]],
      server: Option[WookieeGrpcServer]
  ): Receive =
    super.receive orElse {
      case GrpcServiceDefinition(name, services) =>
        becomeDirty(defs, server, name, services.asScala, initialize = true)

      case _: InitializeServers => // Do nothing, we've already started gRPC server for all registered defs
      case _: CleanCheck        => sender() ! CleanResponse(server.isDefined)
    }

  // From this state we will wait 5 seconds (to let other definitions roll in) then we'll get an
  // InitializeServers call which will trigger the start of the gRPC server
  def dirty(
      defs: Map[String, List[(ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])]],
      server: Option[WookieeGrpcServer]
  ): Receive =
    super.receive orElse {
      case GrpcServiceDefinition(name, services) =>
        becomeDirty(defs, server, name, services.asScala, initialize = false)

      case _: InitializeServers =>
        log.info(s"WGM103: Got message to start gRPC for ${defs.values.flatten.size} gRPC service definitions..")
        val newServer = startGrpc(defs, server)
        if (newServer.isDefined)
          log.info(s"WGM104: Successfully started gRPC Manager's services")
        else
          log.warn(s"WGM105: Failed to start gRPC Manager's services, check logs above")

        this.server.set(newServer)
        context.become(clean(defs, newServer))

      case _: CleanCheck => sender() ! CleanResponse(false)
    }

  private def shutdownCurrent(currentServer: Option[WookieeGrpcServer]): EitherT[IO, WookieeGrpcError, Unit] = {
    (currentServer match {
      case Some(server) => server.shutdown()
      case _            => IO.unit
    }).toEitherT(err => UnknownShutdownError(err.getMessage))
  }

  private def becomeDirty(
      currentDefs: Map[String, List[
        (ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])
      ]],
      server: Option[WookieeGrpcServer],
      groupName: String,
      newDefs: List[GrpcDefinition],
      initialize: Boolean
  ): Unit = {
    import context.dispatcher

    log.info(s"WGM101: Entity [$groupName] wants to load ${newDefs.size} gRPC service definitions..")
    context.become(dirty(currentDefs.updated(groupName, newDefs.map { defin: GrpcDefinition =>
      (defin.definition, defin.authSettings, defin.interceptors)
    }), server))

    if (initialize)
      context.system.scheduler.scheduleOnce(10.seconds, self, InitializeServers())

    sender() ! GrpcDefinitionsAck()
  }

  private def startGrpc(
      definitions: Map[String, List[
        (ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])
      ]],
      currentServer: Option[WookieeGrpcServer]
  ): Option[WookieeGrpcServer] =
    try {
      (for {
        // Shutdown existing server if present
        _ <- shutdownCurrent(currentServer)

        defs = definitions.values.flatten.toList
        server <- defs.headOption match {
          case Some(first) =>
            val someStart = startGrpcServers(NonEmptyList(first, defs.drop(1)), config)
              .map(srv => Some(srv): Option[WookieeGrpcServer])

            someStart.attemptT.leftMap(t => FailedToStartGrpcServerError(t.getMessage): WookieeGrpcError)
          case None =>
            log.info("WGM203: No Service Definitions Provided, Not Starting Server")
            (None: Option[WookieeGrpcServer]).asRight[WookieeGrpcError].toEitherT[IO]
        }
      } yield server)
        .value
        .unsafeRunSync() match {
        case Left(err) =>
          err match {
            case startFail: FailedToStartGrpcServerError =>
              writeError(s"WGM205: Unable to start gRPC: '${startFail.err}'")
            case cdrErr: WookieeGrpcError =>
              writeError(s"WGM206: Unknown error when starting gRPC: '$cdrErr'")
          }

        case Right(server) => server
      }
    } catch {
      case initializerError: ExceptionInInitializerError =>
        writeError(s"WGM210: Failed to start gRPC Service due to below error", Some(initializerError))
      case ex: Throwable =>
        writeError(s"WGM211: Unexpected error when starting gRPC Service", Some(ex))
    }

  private def writeError(msg: String): Option[WookieeGrpcServer] = writeError(msg, None)

  private def writeError(msg: String, t: Option[Throwable]): Option[WookieeGrpcServer] = {
    t match {
      case Some(err) => log.error(msg, err)
      case None      => log.error(msg)
    }
    None
  }

  override def hostConfig: Config = config
}
