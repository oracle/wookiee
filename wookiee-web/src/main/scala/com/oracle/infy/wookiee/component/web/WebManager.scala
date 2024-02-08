package com.oracle.infy.wookiee.component.web

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.component.messages.StatusRequest
import com.oracle.infy.wookiee.component.web.http.HttpCommand
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.{BOTH, EXTERNAL, EndpointType, INTERNAL}
import com.oracle.infy.wookiee.component.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.web.http.impl.WookieeRouter
import com.oracle.infy.wookiee.component.web.http.impl.WookieeRouter.{
  EndpointMeta,
  ServiceHolder,
  WebsocketHandler,
  WookieeHandler
}
import com.oracle.infy.wookiee.component.{ComponentManager, ComponentRequest, ComponentV2}
import com.oracle.infy.wookiee.health.HealthCheckActor
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config
import io.helidon.webserver._
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.bridge.SLF4JBridgeHandler

import java.io.{FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object WebManager extends Mediator[WebManager] {
  val ComponentName = "wookiee-web"

  // Call this to get all the registered endpoints for a given instance
  def getEndpoints(instanceId: String, external: Boolean): List[EndpointMeta] = {
    val manager = getMediator(instanceId)
    manager.getEndpoints(external)
  }

  def getEndpoints(config: Config, external: Boolean): List[EndpointMeta] =
    getEndpoints(getInstanceId(config), external)

  // Defaults to both servers, call this to change the white list of allowed origins via CORS
  def setCORSAllowedOrigins(
      config: Config,
      allowedOrigins: CorsWhiteList,
      endpointType: EndpointType = EndpointType.BOTH
  ): Unit = {
    val manager = getMediator(getInstanceId(config))
    log.info(s"DEBUG : Wookiee : In setCORSAllowedOrigins with ${endpointType}")
    if (endpointType == EndpointType.EXTERNAL || endpointType == EndpointType.BOTH) {
      log.info(s"DEBUG : Wookiee : SEtting for external server..")
      manager.externalServer.routingService.setAllowedOrigins(allowedOrigins)
    }
    if (endpointType == EndpointType.INTERNAL || endpointType == EndpointType.BOTH) {
      log.info(s"DEBUG : Wookiee : SEtting for internal server..")
      manager.internalServer.routingService.setAllowedOrigins(allowedOrigins)
    }
    log.info(s"CORS allowed origins updated to [$allowedOrigins]")
    log.info(
      s"DEBUG : Have we set correctly External is ${manager.externalServer.routingService.allowedOriginsRef} " +
        s"and Internal is ${manager.internalServer.routingService.allowedOriginsRef}"
    )
    log.info(
      s"DEBUG : Comparing the 2 servers External ${manager.externalServer.routingService} " +
        s"and Internal ${manager.internalServer.routingService}"
    )
  }

  case class WookieeWebException(msg: String, cause: Option[Throwable], code: Option[Int] = None)
      extends Exception(msg, cause.orNull)
}

// Main Web Component, manager for HTTP and Websockets
class WebManager(name: String, config: Config) extends ComponentV2(name, config) {
  WebManager.registerMediator(config, this)

  protected[oracle] var internalServer: ServiceHolder = _
  protected[oracle] var externalServer: ServiceHolder = _

  def internalPort: Int = config.getInt(s"${WebManager.ComponentName}.internal-port")
  def externalPort: Int = config.getInt(s"${WebManager.ComponentName}.external-port")

  def getEndpoints(external: Boolean): List[EndpointMeta] =
    if (external) {
      externalServer.routingService.listOfRoutes()
    } else {
      internalServer.routingService.listOfRoutes()
    }

  // Kick off both internal and external web services on startup
  def startService(): Unit = {
    def startServer(routingService: WookieeRouter, port: Int): ServiceHolder = {
      val routing = Routing
        .builder()
        .register("/", routingService)
        .build()

      val serverBuilder = WebServer
        .builder()
        .routing(routing)
        .port(port)

      // Add SSL support if configured
      val server = if (config.hasPath(s"${WebManager.ComponentName}.secure.keystore-path")) {
        val certFile: String = config.getString(s"${WebManager.ComponentName}.secure.keystore-path")
        val password: Array[Char] =
          config.getString(s"${WebManager.ComponentName}.secure.keystore-passphrase").toCharArray

        val ks: KeyStore = KeyStore.getInstance("PKCS12")
        val keystore: InputStream = new FileInputStream(certFile)

        require(keystore != null, "Keystore required!")
        ks.load(keystore, password)

        val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
        keyManagerFactory.init(ks, password)

        val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
        tmf.init(ks)

        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
        val wsTls = WebServerTls.builder().sslContext(sslContext)

        serverBuilder.tls(wsTls.build()).build()
      } else serverBuilder.build()

      server.start()
      ServiceHolder(server, routingService)
    }

    // Check config for the settings of allowed Origin headers
    def getAllowedOrigins(portType: String): CorsWhiteList = {
      val origins = config.getStringList(s"${WebManager.ComponentName}.cors.$portType").asScala.toList
      if (origins.isEmpty || origins.contains("*")) CorsWhiteList.allowAll
      else CorsWhiteList(origins)
    }

    val internalOrigins = getAllowedOrigins("internal-allowed-origins")
    val externalOrigins = getAllowedOrigins("external-allowed-origins")

    log.info(s"DEBUG : Webmanager : Creating internal server with ${internalOrigins}")
    internalServer = startServer(new WookieeRouter(internalOrigins), internalPort)
    log.info(s"DEBUG : Webmanager : Creating external server with ${internalOrigins}")
    externalServer = startServer(new WookieeRouter(externalOrigins), externalPort)
    registerInternalEndpoints()
  }

  // Call on shutdown
  def stopService(): Unit = {
    internalServer.server.shutdown()
    externalServer.server.shutdown()
    ()
  }

  // Main internal entry point for registration of HTTP endpoints
  def registerEndpoint(path: String, endpointType: EndpointType, method: String, handler: WookieeHandler): Unit = {
    val actualMethod = handler match {
      case _: WebsocketHandler[_] => "WS"
      case _                      => method
    }

    val slashPath = if (path.startsWith("/")) path else s"/$path"
    log.debug(s"HM200 Registering Endpoint: [path=$slashPath], [method=$actualMethod], [type=$endpointType]")

    endpointType match {
      case EXTERNAL =>
        externalServer.routingService.addRoute(slashPath, actualMethod, handler)
      case INTERNAL =>
        internalServer.routingService.addRoute(slashPath, actualMethod, handler)
      case BOTH =>
        externalServer.routingService.addRoute(slashPath, actualMethod, handler)
        internalServer.routingService.addRoute(slashPath, actualMethod, handler)
    }

    log.info(s"HM201 Endpoint Registered: Path=[$slashPath], Method=[$actualMethod], Type=[$endpointType]")
  }

  override def start(): Unit = {
    // Redirect Helidon logging (which uses java.util.logging) to SLF4J
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    startService()
    log.info(s"Helidon Servers started on ports: [internal=$internalPort], [external=$externalPort]")
  }

  override def prepareForShutdown(): Unit = {
    stopService()
    log.info("Helidon Server shutdown complete")
  }

  // Register internal healthcheck and metrics endpoints
  private def registerInternalEndpoints(): Unit = {
    implicit val ec: ExecutionContext = ThreadUtil.createEC("internal-endpoint-pool")
    implicit val conf: Config = config

    internalRegister("healthcheck", { _ =>
      HealthCheckActor.getHealthFull.map { health =>
        WookieeResponse(Content(health.toJson))
      }
    })
    internalRegister("healthcheck/full", { _ =>
      HealthCheckActor.getHealthFull.map { health =>
        WookieeResponse(Content(health.toJson))
      }
    })
    internalRegister(
      "healthcheck/lb", { _ =>
        HealthCheckActor.getHealthLB.map { health =>
          // Return should have quotes around it (i.e. "UP")
          WookieeResponse(Content(s""""$health""""))
        }
      }
    )
    internalRegister(
      "healthcheck/nagios", { _ =>
        HealthCheckActor.getHealthNagios.map { health =>
          // Return should have quotes around it (i.e. "NORMAL|Thunderbirds are GO")
          WookieeResponse(Content(s""""$health""""))
        }
      }
    )
    internalRegister("ping", { _ =>
      Future.successful {
        WookieeResponse(Content(s"pong: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}"))
      }
    })
    internalRegister("favicon.ico", { _ =>
      Future.successful {
        WookieeResponse(Content(""), StatusCode(204))
      }
    })
    internalRegister(
      "metrics", { _ =>
        ComponentManager.requestToComponent("wookiee-metrics", ComponentRequest(StatusRequest("string"))).map { resp =>
          WookieeResponse(Content(resp.resp.toString))
        }
      }
    )
  }

  // Helper for registering simple internal endpoints like healthcheck
  private def internalRegister(cmdPath: String, execution: WookieeRequest => Future[WookieeResponse])(
      implicit ec: ExecutionContext,
      conf: Config
  ): Unit = {
    WookieeEndpoints.registerEndpoint(new HttpCommand {
      override val name: String = commandName
      override def commandName: String = cmdPath.split("/").last
      override def method: String = "GET"
      override def path: String = cmdPath
      override def endpointType: EndpointType = EndpointType.INTERNAL
      override def execute(input: WookieeRequest): Future[WookieeResponse] = execution(input)
    })
  }
}
