package com.oracle.infy.wookiee.model

case class HostMetadata(load: Int, quarantined: Boolean)
//final case class Host(version: Long, address: String, port: Int, metadata: Map[String, String])
final case class Host(version: Long, address: String, port: Int, metadata: HostMetadata)