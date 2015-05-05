/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.component.cache

import java.security.MessageDigest

/**
 * This is basically the default cache key that can be overridden by a custom class
 * The default will take the key to MD5 string
 */
class CacheKey(id:Int, key:String, useMD5:Boolean=true) {
  val digest = MessageDigest.getInstance("MD5")

  override def toString() : String = {
    if (useMD5) {
      val text = "%d-%s".format(id, key)
      digest.digest(text.getBytes).map("%02x".format(_)).mkString
    } else {
      key
    }
  }
}
