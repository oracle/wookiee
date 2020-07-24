package com.oracle.infy.wookiee.grpc.tests

import cats.implicits.{catsSyntaxEq => _}
import com.oracle.infy.wookiee.grpc.common.{HostGenerator, UTestScalaCheck}
import com.oracle.infy.wookiee.grpc.json.HostSerde._
import com.oracle.infy.wookiee.model.Host
import org.scalacheck.Prop
import org.scalacheck.Prop.forAll
import utest.{Tests, test}
import com.oracle.infy.wookiee.utils.implicits._

object SerdeTest extends UTestScalaCheck with HostGenerator {

  implicit private class ScalacheckUtils[L](maybeError: Either[L, Boolean]) {
    def toProp: Prop = Prop(maybeError.getOrElse(false))
  }

  def tests: Tests = {
    val hostsSerdeIsSymmetric = {
      forAll { data: Host =>
        deserialize(serialize(data)).map(_ === data).toProp
      }
    }

    Tests {
      test("Host serde must be symmetric") {
        hostsSerdeIsSymmetric.checkUTest()
      }
      test("test") {
        assert(false)
      }

    }
  }
}
