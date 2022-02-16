package com.oracle.infy.wookiee.grpc.model

case class HostMetadata(load: Int, quarantined: Boolean)
final case class Host(version: Long, address: String, port: Int, metadata: HostMetadata)
