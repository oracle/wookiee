/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.component.spray.directive

import com.typesafe.config.{Config, ConfigFactory}
import com.webtrends.harness.authentication.CIDRRules
import org.specs2.mutable.SpecificationWithJUnit
import spray.http.HttpHeaders._
import spray.http.StatusCodes
import spray.testkit.Specs2RouteTest

class CIDRNoIPSpec extends SpecificationWithJUnit with Specs2RouteTest with CIDRDirectives {

  implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.allowConf))

  "the 'cidrFilter' directive" should {

    "reject when there is no source ip" in {
      Get("/good") ~> {
        cidrFilter {
          complete("good")
        }
      } ~> check {
        status === StatusCodes.NotFound
      }
    }
  }
}


class CIDRAllowSpec extends SpecificationWithJUnit with Specs2RouteTest with CIDRDirectives {

  implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.allowConf))

  "the 'cidrFilter' directive" should {

    "accept a defined address using 'allow'" in {
      Get("/good") ~> addHeader(`Remote-Address`("127.0.0.1")) ~> {
        cidrFilter {
          complete("good")
        }
      } ~> check {
        status === StatusCodes.OK
      }
    }

    "accept a defined second address using 'allow'" in {
      implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.allowConf))
      Get("/good") ~> addHeader(`Remote-Address`("10.88.16.32")) ~> {
        cidrFilter {
          complete("good")
        }
      } ~> check {
        status === StatusCodes.OK
      }
    }

    "reject an un-defined address using 'allow'" in {
      implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.allowConf))
      Get("/bad") ~> addHeader(`Remote-Address`("216.64.169.240")) ~> {
        cidrFilter {
          complete("bad")
        }
      } ~> check {
        status === StatusCodes.NotFound
      }
    }
  }
}


class CIDRDenySpec extends SpecificationWithJUnit with Specs2RouteTest with CIDRDirectives {

  implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.denyConf))

  "the 'cidrFilter' directive" should {
    "accept a defined address using 'deny'" in {
      implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.denyConf))
      Get("/good") ~> addHeader(`Remote-Address`("127.0.0.1")) ~> {
        cidrFilter {
          complete("good")
        }
      } ~> check {
        status === StatusCodes.OK
      }
    }

    "reject a defined address using 'deny'" in {
      implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.denyConf))
      Get("/bad") ~> addHeader(`Remote-Address`("10.88.16.32")) ~> {
        cidrFilter {
          complete("bad")
        }
      } ~> check {
        status === StatusCodes.NotFound
      }
    }
  }

}


class CIDRMixSpec extends SpecificationWithJUnit with Specs2RouteTest with CIDRDirectives {

  implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.mixConf))

  "the 'cidrFilter' directive" should {
    "accept a defined address using 'mix'" in {
      implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.mixConf))
      Get("/good") ~> addHeader(`Remote-Address`("127.0.0.1")) ~> {
        cidrFilter {
          complete("good")
        }
      } ~> check {
        status === StatusCodes.OK
      }
    }

    "reject an un-defined address using 'mix'" in {
      implicit var cidrRules:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.mixConf))
      Get("/good") ~> addHeader(`Remote-Address`("216.64.169.240")) ~> {
        cidrFilter {
          complete("bad")
        }
      } ~> check {
        status === StatusCodes.NotFound
      }
    }

    "reject a defined second address using 'mix'" in {
      implicit var settings:Option[CIDRRules] = Some(CIDRRules(CIDRConfig.mixConf))
      Get("/bad") ~> addHeader(`Remote-Address`("10.88.16.32")) ~> {
        cidrFilter {
          complete("bad")
        }
      } ~> check {
        status === StatusCodes.NotFound
      }
    }
  }

}

object CIDRConfig {
  val allowConf: Config = ConfigFactory.parseString( """
        cidr-rules {
          allow=["127.0.0.1/30", "10.0.0.0/8"]
          deny=[]
        }
    """)

  val denyConf: Config = ConfigFactory.parseString( """
        cidr-rules {
          allow=[]
          deny=["10.0.0.0/8"]
        }
    """)

  val mixConf: Config = ConfigFactory.parseString( """
      cidr-rules {
        allow=["127.0.0.1/30"]
        deny=["10.0.0.0/8"]
      }
    """)
}
