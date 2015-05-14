/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:16 PM
 */
package com.webtrends.harness.component.etcd

import com.webtrends.harness.component.Component
import com.webtrends.harness.utils.ConfigUtil

class EtcdManager(name:String) extends Component(name) with Etcd {

  implicit val etcdSettings = EtcdSettings(ConfigUtil.prepareSubConfig(config, name))

  /**
   * Start function will start any child actors that will be managed by the ComponentManager
   * @return
   */
  override def start = {
    startEtcdComponent
    super.start
  }

  /**
   * Stop will execute any cleanup work to be done for the child actors
   * if not necessary this can be deleted
   * @return
   */
  override def stop = {
    stopEtcd
    super.stop
  }
}