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

package com.webtrends.harness.component.socko.utils

import org.mashupbots.socko.events.HttpResponseStatus

class SockoCommandException(val code:HttpResponseStatus, val msg:String, val ex:Option[Throwable]=None) extends Exception(msg, ex.orNull)

object SockoCommandException {
    def apply(code:HttpResponseStatus, msg:String) = new SockoCommandException(code, msg)
    def apply(code:HttpResponseStatus, t:Throwable) = new SockoCommandException(code, t.getMessage, Some(t))
}
