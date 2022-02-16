package com.oracle.infy.wookiee.service

import com.oracle.infy.wookiee.app.Harness
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ${service-name}Integration extends AnyWordSpecLike with Matchers {
  "${service-name} " should {
    " be able to run as a full integration" in {
      while (true) {
        Thread.sleep(100)
      }
      success
    }
  }
}

