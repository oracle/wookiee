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

package com.webtrends.harness.component

/**
 * @author Michael Cuthbert on 12/10/14.
 */
class ComponentException(name:String, msg:String, ex:Option[Throwable]=None) extends Exception(msg, ex.getOrElse(null))

object ComponentException {
  def apply(name:String, msg:String) = new ComponentException(name, msg)
  def apply(name:String, t:Throwable) = new ComponentException(name, t.getMessage, Some(t))
}

class ComponentNotFoundException(name:String, msg:String, ex:Option[Throwable]=None) extends Exception(msg, ex.getOrElse(null))

object ComponentNotFoundException {
  def apply(name:String, msg:String) = new ComponentNotFoundException(name, msg)
  def apply(name:String, t:Throwable) = new ComponentNotFoundException(name, t.getMessage, Some(t))
}