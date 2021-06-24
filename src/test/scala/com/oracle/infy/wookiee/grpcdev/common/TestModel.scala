package com.oracle.infy.wookiee.grpcdev.common

object TestModel {
  case class TestOptionString(maybeString: Option[String])

  case class TestCaseClass(testString: String, testInt: Int)
  case class TestOptionCaseClass(maybeCaseClass: Option[TestCaseClass])

  case class TestOptionOptionString(maybeMaybeString: Option[Option[String]])

  case class TestOptionOptionCaseClass(maybeMaybeCaseClass: Option[Option[TestCaseClass]])
}
