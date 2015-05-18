package com.webtrends.harness.component.kafka.util

import kafka.consumer.SimpleConsumer

class KafkaConsumer(host: String,
                    port: Int,
                    soTimeout: Int,
                    bufferSize: Int,
                    clientId: String) extends SimpleConsumer(host, port, soTimeout, bufferSize, clientId) {
  @volatile
  var closed = false
}
