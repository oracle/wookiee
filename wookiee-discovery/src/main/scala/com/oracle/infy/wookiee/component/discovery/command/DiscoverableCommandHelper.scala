package com.oracle.infy.wookiee.component.discovery.command

import com.oracle.infy.wookiee.Mediator
import com.oracle.infy.wookiee.command.WookieeCommandExecutive
import com.oracle.infy.wookiee.component.discovery.command.grpc.GrpcJITService
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.oracle.infy.wookiee.component.grpc.GrpcManager.GrpcDefinition
import com.oracle.infy.wookiee.grpc.settings.ServiceAuthSettings
import com.typesafe.config.Config
import io.grpc.ServerInterceptor

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

// Will usually be extended by a Wookiee Service to register DiscoverableCommands once on startup
trait DiscoverableCommandHelper {

  /**
    * Wrapper that allows services to add commands to the command manager with a single discoverable command
    * @param command The command to register
    * @param authToken The gRPC auth token to use for the command
    * @param intercepts The gRPC interceptors to use for the command
    */
  def registerDiscoverableCommand[Input <: Any: ClassTag](
      command: DiscoverableCommand[Input, _ <: Any],
      authToken: Option[String] = None,
      intercepts: java.util.List[ServerInterceptor] = List.empty[ServerInterceptor].asJava
  )(implicit config: Config, ec: ExecutionContext): Future[Unit] = Future {
    val wookComExec = WookieeCommandExecutive.getMediator(Mediator.getInstanceId(config))
    // Register command locally (can be called via WookieeCommandExecutive.executeCommand)
    wookComExec.registerCommand(command)
    // Register command to be accessible via remote gRPC call
    GrpcManager.registerGrpcService(
      config,
      command.commandName,
      List(
        new GrpcDefinition(
          new GrpcJITService(command).bindService(),
          authToken.map(ServiceAuthSettings.apply),
          if (intercepts.isEmpty) None else Some(intercepts.asScala.toList)
        )
      )
    )
  }
}
