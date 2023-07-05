package com.oracle.infy.wookiee.discovery.command

import com.google.protobuf.StringValue
import com.oracle.infy.wookiee.component.grpc.GrpcManager
import com.oracle.infy.wookiee.discovery.command.grpc.GrpcDiscoverableStub
import com.oracle.infy.wookiee.grpc.settings.SSLClientSettings
import com.oracle.infy.wookiee.utils.ClassUtil
import com.typesafe.config.Config
import org.json4s.Formats
import org.json4s.jackson.JsonMethods._

import scala.util.Try
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

/**
  * Trait for executing discoverable commands. They should have first been registered
  * on the target server using the registerDiscoverableCommand method in
  * com.oracle.infy.wookiee.component.discovery.command.DiscoverableCommandHelper.
  * @author Spencer Wood
  */
trait DiscoverableCommandExecution {

  /**
    * Executes a discoverable command where ever it may be located. The fields for this command
    * likely are the same ones that can be found in the target server's config file under the
    * path 'wookiee-grpc-component.grpc'
    */
  def executeDiscoverableCommand[Input <: Any: ClassTag, Output <: Any: ClassTag: TypeTag](
      zkPath: String, // In target config: 'wookiee-grpc-component.grpc.zk-discovery-path'
      zkConnect: String, // e.g. localhost:2181, zoo.wookiee.io:2181
      bearerToken: String, // If the target server is using auth, put token here. Can leave as any string otherwise
      sslClientSettings: Option[SSLClientSettings], // In target config: 'wookiee-grpc-component.grpc.ssl'
      commandName: String, // Used to find the command on the target server, result of DiscoverableCommand.commandName
      input: Input, // The actual input for the command, will be (de)serialized on send/receipt
      maxMessageSize: Int = 4194304 // If needing to protect against large messages, or to allow them, use this
  )(implicit formats: Formats, config: Config, ec: ExecutionContext): Future[Output] = Future {
    val stub: GrpcDiscoverableStub = getGenericStub(zkPath, zkConnect, bearerToken, sslClientSettings, maxMessageSize)
    val inputString = ClassUtil.writeAny(input)
    // Send the request to our remote server
    val result = stub.executeRemote(StringValue.of(inputString), commandName)
    parse(result.getValue).extract[Output]
  }

  // Convenience method to check all the usual places that the zookeeper server might be configured
  def getZKConnectConfig(config: Config): Option[String] =
    Try(config.getString("wookiee-zookeeper.quorum"))
      .orElse(Try(config.getString("zookeeper-config.connect-string")))
      .toOption

  private[wookiee] def getGenericStub(
      zkPath: String,
      zkConnect: String,
      bearerToken: String,
      sslClientSettings: Option[SSLClientSettings],
      maxMessageSize: Int = 4194304
  )(implicit config: Config): GrpcDiscoverableStub = {
    // Will be stored in mediator and closed on shutdown
    val channel = GrpcManager.getChannelFromMediator(zkPath, zkConnect, bearerToken, sslClientSettings, maxMessageSize)
    new GrpcDiscoverableStub(channel.managedChannel)
  }

  /**
    * Executes a discoverable command on every server that is hosting it
    * Returns the result of one of the run commands
    * TODO: Implement this using gRPC
    */
//  def broadcastDiscoverableCommand[Input <: AnyRef: ClassTag, Output <: Any: ClassTag](
//      zkPath: String,
//      zkConnect: String,
//      bearerToken: String,
//      sslClientSettings: Option[SSLClientSettings],
//      maxMessageSize: Int,
//      commandName: String,
//      input: Input
//  )(implicit formats: Formats, config: Config, ec: ExecutionContext): Future[Output] = ???
}
