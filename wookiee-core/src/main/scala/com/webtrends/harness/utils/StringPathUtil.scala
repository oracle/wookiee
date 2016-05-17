package com.webtrends.harness.utils


object StringPathUtil {

  implicit class StringPathParsing(s: String) {

    def splitPath(): Array[String] = {
      s.split('/').filter(_.nonEmpty)
    }
  }

}
