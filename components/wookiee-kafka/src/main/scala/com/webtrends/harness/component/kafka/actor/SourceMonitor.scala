package com.webtrends.harness.component.kafka.actor

import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.kafka.actor.KafkaTopicManager.DownSources
import com.webtrends.harness.component.kafka.util.KafkaSettings
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder

import scala.collection.mutable
import scala.io.Source
import scala.util.Try
import scala.util.parsing.json.{JSON, JSONObject}

/**
 * Allows one to setup monitoring on any source nodes to watch for scheduled downtime and put workers into a NORMAL state
 * instead of DEGRADED when it is expected for them to go down
 */
case class HostList(hosts: List[String])

class SourceMonitor extends HActor with KafkaSettings {
  val nagiosServer = Try { kafkaConfig.getString("nagios-host") } getOrElse "http://nagios:8080/host/"

  override def receive: Receive = {
    case HostList(hosts) => sender ! checkHosts(hosts)
  }

  def checkHosts(assignments: List[String]): DownSources = {
    val checkedHosts = mutable.HashSet[String]()
    val downHosts = mutable.HashSet[String]()
    assignments foreach { assignment =>
      if (!checkedHosts.contains(assignment)) {
        checkedHosts.add(assignment)
        if (!hostIsGreen(assignment.split('.')(0))) downHosts.add(assignment)
      }
    }
    DownSources(downHosts.toSet)
  }

  def hostIsGreen(host: String): Boolean = {
    try {
      val response = getRestContent(s"$nagiosServer$host")
      response match {
        case Some(resp) =>
          val parsed = JSON.parseRaw(resp).get.asInstanceOf[JSONObject].obj("content").asInstanceOf[JSONObject]
          parsed.obj("scheduled_downtime_depth") == "0"
        case None => true // If nagios not responding, assume no problems in source, so we can go CRITICAL
      }
    } catch {
      case e: NoSuchElementException => true // In the case where the host is not using this service
      case e: Exception =>
        log.error(e, s"Error parsing nagios response for host $host")
        true
    }
  }

  // Returns the text content from a REST URL. Returns a None if there is a problem.
  def getRestContent(url:String): Option[String] = {
    var content: Option[String] = None
    val httpClient = HttpClientBuilder.create().build()
    try {
      val httpResponse = httpClient.execute(new HttpGet(url))
      val entity = httpResponse.getEntity

      if (entity != null) {
        val inputStream = entity.getContent
        content = Some(Source.fromInputStream(inputStream).getLines().mkString)
        inputStream.close()
      }
    } finally {
      httpClient.close()
    }
    content
  }
}
