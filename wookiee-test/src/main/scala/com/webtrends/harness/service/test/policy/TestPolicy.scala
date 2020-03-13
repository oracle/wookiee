package com.webtrends.harness.service.test.policy

import com.webtrends.harness.policy.Policy


object TestPolicy extends Policy {
  def PolicyName = "TestPolicy"

  /**
   * This function should be implemented by any service that wants to add
   * any commands to make available for use
   */
  override def addCommands: Unit = {
    super.addCommands
  }
}