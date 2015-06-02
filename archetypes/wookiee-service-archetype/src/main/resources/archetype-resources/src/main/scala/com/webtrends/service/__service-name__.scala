/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 9:31 PM
 */
package com.webtrends.service

import com.webtrends.harness.service.Service
import com.webtrends.harness.service.messages.Ready

class ${service-name} extends Service {

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  override def addCommands = {
    addCommand(${service-name}Command.CommandName, classOf[${service-name}Command])
  }

  /**
   * This is the receive expression for your service. Apply any logic you wish
   * to handle specific messages
   */
  override def serviceReceive = ({
    // TODO: Add additional message handlers here
    case Ready(meta) =>
      log.info("I received a Ready message from the outside world")
  }: Receive) orElse super.serviceReceive
}

