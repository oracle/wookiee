package com.oracle.infy.wookiee.communication.ws

import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand.{OutputHolder, getInternalConfig}
import com.oracle.infy.wookiee.communication.command.InternalKafkaCommand
import com.oracle.infy.wookiee.communication.command.InternalKafkaCommand.TopicInfo
import com.oracle.infy.wookiee.component.web.http.HttpObjects
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType
import com.oracle.infy.wookiee.component.web.http.HttpObjects.EndpointType.EndpointType
import com.oracle.infy.wookiee.component.web.ws.WookieeWebsocket
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommandExecution
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommandHelper.{ZookeeperConfig, getZKConnectConfig}
import com.oracle.infy.wookiee.kafka.WookieeKafka
import com.oracle.infy.wookiee.kafka.WookieeKafka.WookieeRecord
import com.typesafe.config.Config
import org.json4s.{DefaultFormats, Formats}

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.websocket.Session
import scala.concurrent.{ExecutionContext, Future}

class KafkaWSHandler(
    implicit ec: ExecutionContext,
    config: Config
) extends WookieeWebsocket[AuthHolder]
    with DiscoverableCommandExecution {
  implicit val formats: Formats = DefaultFormats

  private val kafkaConsumer: AtomicReference[Option[AutoCloseable]] =
    new AtomicReference[Option[AutoCloseable]](None)
  override def path: String = "/ws/kafka/$userId"

  override def endpointType: EndpointType = EndpointType.BOTH

  override def handleAuth(request: HttpObjects.WookieeRequest): Future[Option[AuthHolder]] = Future {
    val userId = request.pathSegments("userId")
    Some(
      AuthHolder("no-auth", userId)
    )
  }

  // Close the kafka consumer when the websocket closes
  override def onClosing(auth: Option[AuthHolder]): Unit =
    kafkaConsumer.getAndSet(None).foreach(_.close())

  override def handleText(text: String, request: HttpObjects.WookieeRequest, authInfo: Option[AuthHolder])(
      implicit session: Session
  ): Unit = {
    val userId = authInfo.map(_.userId).getOrElse("no-auth")
    if (kafkaConsumer.get().isEmpty) {
      val kafkaPort: Int = config.getInt("kafka.port")

      // Start up a consumer to read internal messages and send them back to the client
      Future {
        kafkaConsumer.set(
          Some(
            WookieeKafka.startConsumerAndProcess(
              s"localhost:$kafkaPort",
              "group-id",
              Seq(s"internal-topic-$userId"), { record: WookieeRecord =>
                reply(s"Received message [${new String(record.value)}] from internal server for user [$userId]")
              }
            )
          )
        )
      }
    }

    log.info(s"Received message [$text] from user [$userId]")
    executeDiscoverableCommand[TopicInfo, OutputHolder](
      ZookeeperConfig(
        getInternalConfig(config, "zk-path"),
        getZKConnectConfig(config).getOrElse("localhost:3181"),
        getInternalConfig(config, "bearer-token"),
        None
      ),
      InternalKafkaCommand.commandName,
      TopicInfo(s"internal-topic-$userId", text)
    )
    ()
  }
}
