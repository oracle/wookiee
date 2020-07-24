package com.oracle.infy.wookiee.grpc.common

import com.oracle.infy.wookiee.model.Host
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

trait HostGenerator {

  implicit def hostGenerator: Arbitrary[Host] = {
    Arbitrary(for {
      address <- (Gen.alphaNumStr).suchThat(_.nonEmpty)
      version <- arbitrary[Long]
      port <- Gen.choose[Int](0, 9999)
      metadata <- arbitrary[Map[String, String]]
    } yield Host(version, address, port, metadata))
  }

  implicit def hostsGenerator: Arbitrary[Set[Host]] =
    Arbitrary(
      for {
        list <- Gen.listOfN(10, hostGenerator.arbitrary)
      } yield list.toSet
    )
}
