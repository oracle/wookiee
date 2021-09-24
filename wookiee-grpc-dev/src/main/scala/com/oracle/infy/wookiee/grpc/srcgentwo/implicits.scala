package com.oracle.infy.wookiee.grpc.srcgentwo

object implicits {

  implicit class MultiversalEquality[T](left: T) {
    def ===(right: T): Boolean = left == right //scalafix:ok
    def /==(right: T): Boolean = left != right //scalafix:ok
    def =/=(right: T): Boolean = left /== right
  }
}
