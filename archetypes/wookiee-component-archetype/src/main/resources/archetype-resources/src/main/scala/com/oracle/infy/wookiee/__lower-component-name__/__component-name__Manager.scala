/*
 * Copyright (c) 2014. Webtrends (http://www.oracle.infy.com)
 * @author cuthbertm on 11/20/14 12:16 PM
 */
package com.oracle.infy.wookiee.component.${component-name.toLowerCase()}

import com.oracle.infy.wookiee.component.Component

case class ${component-name}Message()

class ${component-name}Manager(name:String) extends Component(name) with ${component-name} {

  /**
   * We add super.receive because if you override the receive message from the component
   * and then do not include super.receive it will not handle messages from the
   * ComponentManager correctly and basically not start up properly
   *
   * @return
   */
  override def receive = super.receive orElse {
    case ${component-name}Message => "DO SOMETHING HERE"
  }

  /**
   * Start function will start any child actors that will be managed by the ComponentManager
   * @return
   */
  override def start = {
    start${component-name}
    super.start
  }

  /**
   * Stop will execute any cleanup work to be done for the child actors
   * if not necessary this can be deleted
   * @return
   */
  override def stop = {
    stop${component-name}
    super.stop
  }
}