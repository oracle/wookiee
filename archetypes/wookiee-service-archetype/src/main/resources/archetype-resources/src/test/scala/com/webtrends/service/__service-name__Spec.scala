/*
 * Copyright (c) $date.year. Webtrends (http://www.webtrends.com)
 * @author $user on $date.get('MM/dd/yyyy hh:mm a')
 */
package com.webtrends.service

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

class ${service-name}Spec extends Specification with NoTimeConversions {

  //TODO TestHarness needs to be rebuilt for mocking the harness correctly
  //Harness.startActorSystem

  "${service-name} " should {

    " be able to be loaded and pinged" in {
      true
    }
  }

  /*step {
    Harness.shutdownActorSystem(false) {
      System.exit(0)
    }
  }*/
}
