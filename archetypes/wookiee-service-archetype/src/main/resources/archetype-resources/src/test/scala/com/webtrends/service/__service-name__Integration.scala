/*
 * Copyright (c) $date.year. Webtrends (http://www.webtrends.com)
 * @author $user on $date.get('MM/dd/yyyy hh:mm a')
 */
package com.webtrends.service

import com.webtrends.harness.app.Harness
import org.specs2.mutable.Specification

class ${service-name}Integration extends Specification {

  //TODO TestHarness needs to be rebuilt for mocking the harness correctly
  //Harness.startActorSystem

  "${service-name} " should {
    " be able to run as a full integration" in {
      while (true) {
        Thread.sleep(100)
      }
      success
    }
  }

  /*step {
    Harness.shutdownActorSystem(false) {
      System.exit(0)
    }
  }*/
}

