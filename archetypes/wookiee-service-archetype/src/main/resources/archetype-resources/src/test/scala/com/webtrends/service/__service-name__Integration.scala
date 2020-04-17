package com.webtrends.service

import com.webtrends.harness.app.Harness
import org.scalatest.{MustMatchers, WordSpecLike}

class ${service-name}Integration extends WordSpecLike with MustMatchers {
  "${service-name} " should {
    " be able to run as a full integration" in {
      while (true) {
        Thread.sleep(100)
      }
      success
    }
  }
}

