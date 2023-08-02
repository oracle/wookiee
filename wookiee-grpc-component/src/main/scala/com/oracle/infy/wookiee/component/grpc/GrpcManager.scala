package com.oracle.infy.wookiee.component.grpc

import akka.actor.ActorSystem
import cats.data.{EitherT, NonEmptyList}
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.implicits._
import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.actor.WookieeActor.Receive
import com.oracle.infy.wookiee.component.ComponentV2
import com.oracle.infy.wookiee.component.grpc.server.GrpcServer
import com.oracle.infy.wookiee.grpc.WookieeGrpcUtils.DEFAULT_MAX_MESSAGE_SIZE
import com.oracle.infy.wookiee.grpc.errors.Errors._
import com.oracle.infy.wookiee.grpc.impl.GRPCUtils.curatorFramework
import com.oracle.infy.wookiee.grpc.model.LoadBalancers.RoundRobinPolicy
import com.oracle.infy.wookiee.grpc.settings.{
  ChannelSettings,
  ClientAuthSettings,
  SSLClientSettings,
  ServiceAuthSettings
}
import com.oracle.infy.wookiee.grpc.utils.implicits.{Java2ScalaConverterList, _}
import com.oracle.infy.wookiee.grpc.{WookieeGrpcChannel, WookieeGrpcServer}
import com.oracle.infy.wookiee.health.{ComponentState, HealthComponent}
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config
import io.grpc.{ServerInterceptor, ServerServiceDefinition}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.retry.RetryForever

import java.util.concurrent.atomic.AtomicReference
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

// Useful for storing channels that have already been created and re-using them
object GrpcChannelManager extends Mediator[TrieMap[ChannelKey, WookieeGrpcChannel]]
case class ChannelKey(zkPath: String, zkConnect: String, bearerToken: String)

object GrpcManager extends Mediator[GrpcManager] {
  val ComponentName = "wookiee-grpc-component"

  // This instance of GrpcManager
  // @throws IllegalStateException if manager has not been registered
  def getGrpcManager(system: ActorSystem): GrpcManager = getGrpcManager(system.settings.config)
  def getGrpcManager(config: Config): GrpcManager = getMediator(config)

  /**
    * Convenience method to register a set of GrpcDefinitions, they will be added to the services already registered
    * @param system Main Wookiee Actor System, should be accessible as system, context.system, or actorSystem in any Actor
    * @param groupName Name of this group of gRPC services, the value is arbitrary but should be unique for each list of definitions on a server
    * @param defs The list of GrpcDefinition objects we'll use to know what to register
    * @throws IllegalStateException If manager actor hasn't started yet, consider calling waitForManager(system) if this happens
    */
  def registerGrpcService(system: ActorSystem, groupName: String, defs: java.util.List[GrpcDefinition]): Unit =
    registerGrpcService(system, groupName, defs.asScala)

  def registerGrpcService(config: Config, groupName: String, defs: List[GrpcDefinition]): Unit = {
    val manager = getGrpcManager(config)
    manager ! GrpcServiceDefinition(groupName, defs.asJava)
  }

  def registerGrpcService(system: ActorSystem, groupName: String, defs: List[GrpcDefinition]): Unit = {
    val manager = getGrpcManager(system)
    manager ! GrpcServiceDefinition(groupName, defs.asJava)
  }

  // Tell a 'dirty' GrpcManager to initialize its endpoints right away instead of waiting for more
  // registrations to come in. Useful for unit tests and hot deploys, for example. Has no effect
  // if initialization has already happened without new registrations
  def initializeGrpcNow(system: ActorSystem): Unit =
    getGrpcManager(system) ! InitializeServers()

  def initializeGrpcNow(config: Config): Unit =
    getGrpcManager(config) ! InitializeServers()

  def waitForManager(system: ActorSystem): Unit =
    waitForManager(system, waitForClean = false)

  def waitForManager(system: ActorSystem, waitForClean: Boolean): Unit =
    waitForManager(system, waitForClean = waitForClean, 30)

  def waitForManager(system: ActorSystem, waitForClean: Boolean, secondsToWait: Int): Unit =
    waitForManager(system.settings.config, waitForClean = waitForClean, secondsToWait = secondsToWait)

  // Call this to ensure that gRPC Manager has finished registering gRPC services, useful for testing
  // Only use 'waitForClean = true' if you've already called registerGrpcService(..) and want to wait for gRPC to come up
  def waitForManager(config: Config, waitForClean: Boolean, secondsToWait: Int): Unit = {
    log.info("WGM300: Waiting for grpc manager to report clean")
    import scala.concurrent.ExecutionContext.Implicits.global

    def cleanCheck(): Boolean = {
      maybeGetMediator(getInstanceId(config)) match {
        case Some(grpcManagerActor) =>
          try {
            val resp = Await.result((grpcManagerActor ? CleanCheck()).mapTo[CleanResponse], 5.seconds)
            if (waitForClean) resp.clean else true
          } catch {
            case _: Throwable =>
              log.debug("WGM301: Failed once to get gRPC Manager, retrying until we're out of time")
              false
          }
        case None => false
      }
    }

    val cleanWaiter = Future {
      while (!cleanCheck()) {} // scalafix:ok
    }
    Await.result(cleanWaiter, secondsToWait.seconds)
    log.info("WGM302: Grpc Manager is clean and ready to go")
  }

  def createChannel(zkPath: String, zkConnect: String, bearerToken: String): WookieeGrpcChannel =
    createChannel(zkPath, zkConnect, bearerToken, None)

  def createChannel(
      zkPath: String,
      zkConnect: String,
      bearerToken: String,
      sslClientSettings: Option[SSLClientSettings]
  ): WookieeGrpcChannel =
    createChannel(zkPath, zkConnect, bearerToken, sslClientSettings, DEFAULT_MAX_MESSAGE_SIZE)

  /**
    * Useful method for creating a gRPC channel to an existing gRPC service,
    * such as the ones GrpcManager is capable of starting
    *
    * @param zkPath Config at 'wookiee-grpc-component.grpc.zk-discovery-path'
    * @param zkConnect Zookeeper connect string, e.g. 'localhost:2121'
    * @param bearerToken Optional bearer token if one is setup on the server, null or empty string otherwise
    * @param sslClientSettings Optional settings for certs, TLS, and service authority if needed by target
    * @param maxMessageSize The limit of how large (in Bytes) a response message can be
    * @return A WookieeGrpcChannel that has a field .managedChannel() that can be put into stubs
    */
  def createChannel(
      zkPath: String,
      zkConnect: String,
      bearerToken: String,
      sslClientSettings: Option[SSLClientSettings],
      maxMessageSize: Int
  ): WookieeGrpcChannel = {

    val zkEC: ExecutionContext = ThreadUtil.createEC(s"curator-blocking-${System.currentTimeMillis()}")
    val curator = curatorFramework(
      zkConnect,
      zkEC,
      new RetryForever(5000)
    )
    curator.start()
    createChannel(zkPath, curator, bearerToken, sslClientSettings, maxMessageSize)
  }

  /**
    * Will return a channel from the mediator if it exists,
    * otherwise it will create a new channel and add it to the mediator
    */
  def getChannelFromMediator(
      zkPath: String,
      zkConnect: String,
      bearerToken: String,
      sslClientSettings: Option[SSLClientSettings] = None,
      maxMessageSize: Int = 4194304
  )(implicit config: Config): WookieeGrpcChannel = {
    val channelMap = GrpcChannelManager.getMediator(getInstanceId(config))
    channelMap.get(ChannelKey(zkPath, zkConnect, bearerToken)) match {
      case Some(channel) =>
        channel
      case None =>
        val channel = createChannel(zkPath, zkConnect, bearerToken, sslClientSettings, maxMessageSize)
        channelMap.put(ChannelKey(zkPath, zkConnect, bearerToken), channel)
        channel
    }
  }

  def createChannel(
      zkPath: String,
      curatorFramework: CuratorFramework,
      bearerToken: String,
      sslClientSettings: Option[SSLClientSettings],
      maxMessageSize: Int
  ): WookieeGrpcChannel = {
    import cats.effect.unsafe.implicits.global

    val ec: ExecutionContext = ThreadUtil.createEC(s"grpc-channel-${System.currentTimeMillis()}")
    val blockingEC: ExecutionContext = ThreadUtil.createEC(s"grpc-blocking-${System.currentTimeMillis()}")
    val offloadEC: ExecutionContext = ThreadUtil.createEC(s"grpc-offload-${System.currentTimeMillis()}")
    implicit val dispatcher: Dispatcher[IO] = ThreadUtil.dispatcherIO()

    def channelSettings(curatorFramework: CuratorFramework) =
      ChannelSettings(
        serviceDiscoveryPath = zkPath,
        eventLoopGroupExecutionContext = blockingEC,
        channelExecutionContext = ec,
        offloadExecutionContext = offloadEC,
        eventLoopGroupExecutionContextThreads = 4,
        lbPolicy = RoundRobinPolicy,
        curatorFramework = curatorFramework,
        sslClientSettings = sslClientSettings,
        clientAuthSettings = Option(bearerToken).filter(_.nonEmpty).map(ClientAuthSettings.apply)
      ).withMaxMessageSize(maxMessageSize)

    (for {
      channel <- WookieeGrpcChannel.of(channelSettings(curatorFramework))
    } yield {
      channel
    }).unsafeRunSync()
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
class GrpcManager(name: String, config: Config) extends ComponentV2(name, config) with GrpcServer with WookieeActor {
  import GrpcManager._

  private val server: AtomicReference[Option[WookieeGrpcServer]] = new AtomicReference(None)

  private val healthRef: AtomicReference[HealthComponent] = new AtomicReference(
    HealthComponent(name, ComponentState.NORMAL, "gRPC Manager awaiting service definitions")
  )

  private val retryDuration =
    Try(config.getInt(s"${GrpcManager.ComponentName}.grpc.retry-delay-sec")).getOrElse(5).seconds

  override def preStart(): Unit = {
    super.preStart()

    log.info(s"WGM100: Starting up CDR Extension gRPC Manager at path [${self.path}]")
    // Register this actor so others can access it
    GrpcManager.registerMediator(config, this)
    GrpcChannelManager.registerMediator(config, new TrieMap[ChannelKey, WookieeGrpcChannel]())
    become(clean(Map(), None))
  }

  override def postStop(): Unit = {
    super.postStop()

    log.info(s"WGM400: Stopping the gRPC Manager and shutting down server..")
    GrpcManager.unregisterMediator(config)
    GrpcChannelManager.maybeGetMediator(config).foreach(_.values.foreach(_.shutdown(true)))
    GrpcChannelManager.unregisterMediator(config)
    server
      .get()
      .map(_.shutdown().unsafeToFuture())
      .getOrElse(Future.successful({}))
      .foreach { _ =>
        log.info("WGM402: Stopping gRPC curator..")
        getCurator.close()
      }
  }

  // We will usually be in this state, until we get a GrpcServiceDefinition call which sends us into the dirty state
  def clean(
      defs: Map[String, List[(ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])]],
      server: Option[WookieeGrpcServer]
  ): Receive =
    super.receive orElse {
      case GrpcServiceDefinition(name, services) =>
        becomeDirty(defs, server, name, services.asScala, initialize = true)

      case _: InitializeServers  => // Do nothing, we've already started gRPC server for all registered defs
      case _: CleanCheck         => sender() ! CleanResponse(server.isDefined)
      case _: CleanResponse      => // Do nothing, this is for actors looking for gRPC Manager's status
      case _: GrpcDefinitionsAck => // Do nothing, this is for other actors that want an Ack
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
        if (newServer.isDefined) {
          log.info(s"WGM104: Successfully started gRPC Manager's services")
          // Server came up, can now transition to clean state
          this.server.set(newServer)
          become(clean(defs, newServer))
          // Flip health to normal
          healthRef.set(HealthComponent(name, ComponentState.NORMAL, "gRPC Manager started and ready for requests"))
        } else {
          // If failed to register gRPC server, reschedule to try again
          log.warn(s"WGM204: Failed to start gRPC Manager's services, retrying in $retryDuration")
          scheduleOnce(retryDuration, self, InitializeServers())
          // Flip health to critical until we can start gRPC server
          healthRef.set(
            HealthComponent(
              name,
              ComponentState.CRITICAL,
              "gRPC Manager failed to start, but will keep trying: Search logs for 'WGM2*'"
            )
          )
          ()
        }

      case _: CleanCheck         => sender() ! CleanResponse(false)
      case _: CleanResponse      => // Do nothing, this is for actors looking for gRPC Manager's status
      case _: GrpcDefinitionsAck => // Do nothing, this is for other actors that want an Ack
    }

  override def getHealth: Future[HealthComponent] =
    Future.successful(healthRef.get())

  private def shutdownCurrent(currentServer: Option[WookieeGrpcServer]): EitherT[IO, WookieeGrpcError, Unit] =
    (currentServer match {
      case Some(server) =>
        log.info("WGM202: Shutting down existing gRPC server..")
        server.shutdown()
      case _ => IO.unit
    }).toEitherT(err => UnknownShutdownError(err.getMessage))

  private def becomeDirty(
      currentDefs: Map[String, List[
        (ServerServiceDefinition, Option[ServiceAuthSettings], Option[List[ServerInterceptor]])
      ]],
      server: Option[WookieeGrpcServer],
      groupName: String,
      newDefs: List[GrpcDefinition],
      initialize: Boolean
  ): Unit = {

    log.info(s"WGM102: Entity [$groupName] wants to load ${newDefs.size} gRPC service definitions..")
    become(dirty(currentDefs.updated(groupName, newDefs.map { defin: GrpcDefinition =>
      (defin.definition, defin.authSettings, defin.interceptors)
    }), server))

    if (initialize)
      scheduleOnce(10.seconds, self, InitializeServers())

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
