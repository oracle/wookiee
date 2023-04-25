package com.oracle.infy.wookiee.grpc.srcgen

object implicits {

  implicit class MultiversalEquality[T](left: T) {
    def ===(right: T): Boolean = left == right
    def /==(right: T): Boolean = left != right
    def =/=(right: T): Boolean = left /== right
  }
}
