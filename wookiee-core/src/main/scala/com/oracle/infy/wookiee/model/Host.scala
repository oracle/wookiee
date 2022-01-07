package com.oracle.infy.wookiee.model

final case class Host(version: Long, address: String, port: Int, metadata: Map[String, String])
