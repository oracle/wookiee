package com.oracle.infy.wookiee.component.helidon

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.component.helidon.web.WookieeEndpoints
import com.oracle.infy.wookiee.component.helidon.web.http.HttpCommand
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects.EndpointType.{BOTH, EXTERNAL, EndpointType, INTERNAL}
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter
import com.oracle.infy.wookiee.component.helidon.web.http.impl.WookieeRouter._
import com.oracle.infy.wookiee.component.messages.StatusRequest
import com.oracle.infy.wookiee.component.{ComponentManager, ComponentRequest, ComponentV2}
import com.oracle.infy.wookiee.health.HealthCheckActor
import com.oracle.infy.wookiee.utils.ThreadUtil
import com.typesafe.config.Config
import io.helidon.webserver._
import org.joda.time.{DateTime, DateTimeZone}

import java.io.{FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object HelidonManager extends Mediator[HelidonManager] {
  val ComponentName = "wookiee-helidon"
}

// Main Helidon Component, manager of all things Helidon but for now mainly HTTP and Websockets
class HelidonManager(name: String, config: Config) extends ComponentV2(name, config) {
  HelidonManager.registerMediator(config, this)

  protected[oracle] var internalServer: ServiceHolder = _
  protected[oracle] var externalServer: ServiceHolder = _

  def internalPort: Int = config.getInt(s"${HelidonManager.ComponentName}.web.internal-port")
  def externalPort: Int = config.getInt(s"${HelidonManager.ComponentName}.web.external-port")

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
      val server = if (config.hasPath(s"${HelidonManager.ComponentName}.web.secure.keystore-path")) {
        val certFile: String = config.getString(s"${HelidonManager.ComponentName}.web.secure.keystore-path")
        val password: Array[Char] =
          config.getString(s"${HelidonManager.ComponentName}.web.secure.keystore-passphrase").toCharArray

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
      val origins = config.getStringList(s"${HelidonManager.ComponentName}.web.cors.$portType").asScala.toList
      if (origins.isEmpty || origins.contains("*")) CorsWhiteList()
      else CorsWhiteList(origins)
    }

    val internalOrigins = getAllowedOrigins("internal-allowed-origins")
    val externalOrigins = getAllowedOrigins("external-allowed-origins")

    internalServer = startServer(new WookieeRouter(internalOrigins), internalPort)
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
    internalRegister("healthcheck/lb", { _ =>
      HealthCheckActor.getHealthLB.map { health =>
        WookieeResponse(Content(health))
      }
    })
    internalRegister("healthcheck/nagios", { _ =>
      HealthCheckActor.getHealthNagios.map { health =>
        WookieeResponse(Content(health))
      }
    })
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
      override def commandName: String = cmdPath.split("/").last
      override def method: String = "GET"
      override def path: String = cmdPath
      override def endpointType: EndpointType = EndpointType.BOTH
      override def execute(input: WookieeRequest): Future[WookieeResponse] = execution(input)
    })
  }
}
