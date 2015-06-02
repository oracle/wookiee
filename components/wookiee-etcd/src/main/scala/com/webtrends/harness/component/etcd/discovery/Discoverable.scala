package com.webtrends.harness.component.etcd.discovery

import com.webtrends.harness.service.Service


trait Discoverable extends EtcdHelper with ServiceIdentity{
  this : Service =>
  publish(identity(), announcement())

  override def identity(): String = {
    serviceName
  }
}

trait ServiceIdentity {
  def identity(): String
  def announcement(): String
}