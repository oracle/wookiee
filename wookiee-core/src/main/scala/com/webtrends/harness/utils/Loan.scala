package com.webtrends.harness.utils

class Loan[A <: AutoCloseable](resource: A) {
  def to[B](block: A => B) = {
    var t: Throwable = null
    try {
      block(resource)
    } catch {
      case ex: Throwable => t = ex; throw ex
    } finally {
      if (resource != null) {
        if (t != null) {
          try {
            resource.close()
          } catch {
            case y: Throwable => t.addSuppressed(y)
          }
        } else {
          resource.close()
        }
      }
    }
  }
}

object Loan {
  def loan[A <: AutoCloseable](resource: A) = new Loan(resource)
}
