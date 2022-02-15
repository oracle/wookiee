package com.oracle.infy.wookiee.health.model

sealed trait State {
  def toStr: String
}

case object Normal extends State {
  val toStr = "NORMAL"
}

case object Critical extends State {
  val toStr = "CRITICAL"
}

case object Degraded extends State {
  val toStr = "DEGRADED"
}

case class WookieeHealth(state: State, details: String, components: Map[String, WookieeHealth] = Map.empty)
