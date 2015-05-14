package com.webtrends.harness.component.etcd.discovery

import scala.concurrent.Future

trait EtcdHelper {
  val path:String

  def locate(path:String): Future[AnyRef]

  def list(path:String): Future[List[AnyRef]]

}
