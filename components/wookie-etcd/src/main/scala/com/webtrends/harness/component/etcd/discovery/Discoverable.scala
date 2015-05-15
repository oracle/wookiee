package com.webtrends.harness.component.etcd.discovery

import com.webtrends.harness.service.Service


trait Discoverable extends EtcdHelper with ServiceIdentity{
  this : Service =>
  publish(serviceName, identity())
}

trait ServiceIdentity {
  def identity(): AnyRef
}