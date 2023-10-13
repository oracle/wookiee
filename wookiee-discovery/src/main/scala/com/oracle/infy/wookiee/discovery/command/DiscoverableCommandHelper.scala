package com.oracle.infy.wookiee.discovery.command

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.oracle.infy.wookiee.component.grpc.GrpcManager.GrpcDefinition
import com.oracle.infy.wookiee.discovery.command.grpc.GrpcJITService
import com.oracle.infy.wookiee.grpc.settings.{SSLClientSettings, ServiceAuthSettings}
import com.typesafe.config.Config
import io.grpc.ServerInterceptor

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Try

// Will usually be extended by a Wookiee Service to register DiscoverableCommands once on startup
trait DiscoverableCommandHelper {

  // Helper method to register a discoverable command, identical to the one in the companion object
  def registerDiscoverableCommand[Input <: Any: ClassTag](
      command: => DiscoverableCommand[Input, _ <: Any],
      authToken: Option[String] = None,
      intercepts: java.util.List[ServerInterceptor] = List.empty[ServerInterceptor].asJava
  )(implicit config: Config, ec: ExecutionContext): Unit = {
    DiscoverableCommandHelper.registerDiscoverableCommand[Input](command, authToken, intercepts)
  }
}

object DiscoverableCommandHelper extends DiscoverableCommandExecution {

  // Convenience method to check all the usual places that the zookeeper server might be configured
  def getZKConnectConfig(config: Config): Option[String] =
    Try(config.getString("wookiee-zookeeper.quorum"))
      .orElse(Try(config.getString("zookeeper-config.connect-string")))
      .toOption

  // Config for execution of discoverable commands
  case class ZookeeperConfig(
      zkPath: String, // In target config: 'wookiee-grpc-component.grpc.zk-discovery-path'
      zkConnect: String, // e.g. localhost:2181, zoo.wookiee.io:2181
      bearerToken: String, // If the target server is using auth, put token here. Can leave as any string otherwise
      sslClientSettings: Option[SSLClientSettings] // In target config: 'wookiee-grpc-component.grpc.ssl'
  )

  /**
    * Static method that allows services to add commands to the command manager with a single discoverable command
    * @param command The command to register
    * @param authToken The gRPC auth token to use for the command
    * @param intercepts The gRPC interceptors to use for the command
    */
  def registerDiscoverableCommand[Input <: Any: ClassTag](
      command: => DiscoverableCommand[Input, _ <: Any],
      authToken: Option[String] = None,
      intercepts: java.util.List[ServerInterceptor] = List.empty[ServerInterceptor].asJava
  )(implicit config: Config, ec: ExecutionContext): Unit = {
    val wookComExec = WookieeCommandExecutive.getMediator(Mediator.getInstanceId(config))
    // Register command locally with one routee (can be called via WookieeCommandExecutive.executeCommand)
    wookComExec.registerCommand(command, 1)
    // Register command to be accessible via remote gRPC call
    val instance = WookieeActor.actorOf(command)
    GrpcManager.registerGrpcService(
      config,
      instance.commandName,
      List(
        new GrpcDefinition(
          new GrpcJITService(instance).bindService(),
          authToken.map(ServiceAuthSettings.apply),
          if (intercepts.isEmpty) None else Some(intercepts.asScala.toList)
        )
      )
    )
  }
}
