package com.oracle.infy.wookiee.component.helidon

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.component.ComponentV2
import com.oracle.infy.wookiee.component.helidon.web.http.HttpObjects._
import com.oracle.infy.wookiee.component.helidon.web.http.RoutingHelper
import com.typesafe.config.Config
import io.helidon.webserver._

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object HelidonManager extends Mediator[HelidonManager] {
  val ComponentName = "wookiee-helidon"

  def registerEndpoint[Input <: Product: ClassTag: TypeTag, Output: ClassTag: TypeTag](
      // TODO Stuff this into a Command with this name
      name: String,
      path: String,
      method: String,
      endpointType: EndpointType.EndpointType,
      requestHandler: WookieeRequest => Future[Input],
      businessLogic: Input => Future[Output],
      responseHandler: Output => WookieeResponse,
      errorHandler: Throwable => WookieeResponse,
      // TODO Incorporate options
      endpointOptions: EndpointOptions = EndpointOptions.default
  )(
      implicit config: Config,
      ec: ExecutionContext
  ): Unit = {
    val mediator = getMediator(config)
    val handler: Handler = (req, res) =>
      try {
        val pathSegments = RoutingHelper.getPathSegments(path, req.path().segments().asScala.toList)

        // Get the query parameters on the request
        val queryParams = req.queryParams().toMap.asScala.toMap.map(x => x._1 -> x._2.asScala.toList)
        val content = req.content().as(classOf[Array[Byte]])

        val wookieeRequest = WookieeRequest(
          Content(content.get()),
          pathSegments,
          queryParams,
          Headers(req.headers().toMap.asScala.toMap.map(x => x._1 -> x._2.asScala.toList))
        )

        requestHandler(wookieeRequest).flatMap(businessLogic).map(responseHandler)
        ()
      } catch {
        case e: Throwable =>
          RoutingHelper.handleErrorAndRespond[Input, Output](errorHandler, res, e)
      }

    mediator.registerEndpoint(path, endpointType, method, handler)
  }

}

class HelidonManager(name: String, config: Config) extends ComponentV2(name, config) with RoutingHelper {
  HelidonManager.registerMediator(config, this)

  override def internalPort: Int = config.getInt(s"${HelidonManager.ComponentName}.web.internal-port")
  override def externalPort: Int = config.getInt(s"${HelidonManager.ComponentName}.web.external-port")

  override def start(): Unit = {
    startService()
    println(s"Helidon Servers started on ports: [internal=$internalPort], [external=$externalPort]")
  }

  override def prepareForShutdown(): Unit = {
    stopService()
    println("Helidon Server shutdown complete")
  }

}
