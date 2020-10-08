package com.oracle.infy.wookiee.grpc

import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry

object ZookeeperUtils {

  def curatorFactory(connStr: String): CuratorFramework = {
    CuratorFrameworkFactory
      .builder()
      .connectString(connStr)
      .retryPolicy(new ExponentialBackoffRetry(1000, 3000))
      .build()
  }

  def createDiscoveryPath(connStr: String, path: String): Unit = {
    val curator = curatorFactory(connStr)
    curator.start()
    curator.create.orSetData().forPath(path)
    curator.close()
  }
}
