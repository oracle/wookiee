package com.webtrends.harness.component.kafka.actor

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef}
import com.webtrends.harness.component.kafka.actor.AssignmentDistributorLeader.PartitionAssignment
import com.webtrends.harness.logging.ActorLoggingAdapter

/**
 * Created with IntelliJ IDEA.
 * User: plataa
 * Date: 4/21/15
 * Time: 3:08 PM
 */
class TestPartitionWorker(kafkaProxy: ActorRef, assign: PartitionAssignment, offsetManager: ActorRef)
  extends Actor with ActorLoggingAdapter {

  log.info(s"Created ${assign.assignmentName}")

  override def receive: Receive = {
    case msg => log.info(s"Got $msg")
  }
}
