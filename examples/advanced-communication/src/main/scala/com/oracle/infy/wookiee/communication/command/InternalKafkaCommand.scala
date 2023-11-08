package com.oracle.infy.wookiee.communication.command

import com.oracle.infy.wookiee.actor.WookieeActor
import com.oracle.infy.wookiee.communication.command.InternalDiscoverableCommand.{InputHolder, OutputHolder}
import com.oracle.infy.wookiee.communication.command.InternalKafkaCommand.TopicInfo
import com.oracle.infy.wookiee.discovery.command.DiscoverableCommand
import com.oracle.infy.wookiee.kafka.WookieeKafka
import com.oracle.infy.wookiee.kafka.produce.WookieeKafkaProducer
import com.typesafe.config.Config

import scala.concurrent.Future

object InternalKafkaCommand {
  val commandName = "internalKafkaCommand"

  case class TopicInfo(outputTopic: String, text: String)
}

class InternalKafkaCommand(config: Config) extends DiscoverableCommand[TopicInfo, OutputHolder] {
  var kafkaProducer: WookieeKafkaProducer = _
  override def commandName: String = InternalKafkaCommand.commandName

  override protected def preStart(): Unit =
    kafkaProducer = WookieeKafka.startProducer(s"localhost:${config.getInt("kafka.port")}")

  // Gets a command to produce one record to a kafka topic
  override def execute(args: TopicInfo): Future[OutputHolder] = {
    log.info(s"Executing command: $commandName, [${args.text}]")
    kafkaProducer.send(args.outputTopic, None, args.text)
    Future.successful(OutputHolder(s"Ignored output [${args}]"))
  }
}
