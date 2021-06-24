package com.oracle.infy.wookiee.grpcdev.common

object TestModel {
  sealed trait TestTypes
  case class TestOptionString(maybeString: Option[String]) extends TestTypes
}
