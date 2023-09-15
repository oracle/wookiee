/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.oracle.infy.wookiee.utils

class Loan[A <: AutoCloseable](resource: A) {

  def to[B](block: A => B): B = {
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
