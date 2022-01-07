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
package com.oracle.infy.wookiee.service

import java.net.{URL, URLClassLoader}
import scala.util.{Success, Try}

class ServiceClassLoader(urls: Seq[URL], parent: ClassLoader) extends URLClassLoader(urls.toArray, parent) {

  /**
    * This method will perform the same functionality as ClassLoader.loadClass, except that it
    * will only locate and load the class in it's own class loader.
    */
  def loadClassLocally(name: String, resolve: Boolean): Option[Class[_]] = {
    // First see if the class is loaded
    (findLoadedClass(name) match {
      case null =>
        // Since the class was already searched for through the parents and local loader,
        // we will now just search for the class here
        Try[Class[_]](findClass(name))
      case c =>
        Success(c)
    }) match {
      case Success(clazz) =>
        val _ = findLoadedClass(name)
        if (resolve) resolveClass(clazz)
        Some(clazz)
      case _ =>
        None
    }
  }

  def getLoadedClass(name: String): Option[Class[_]] = findLoadedClass(name) match {
    case null  => None
    case clazz => Some(clazz)
  }

  /**
    * Appends the specified URL to the list of URLs to search for
    * classes and resources.
    * <p>
    * If the URL specified is <code>null</code> or is already in the
    * list of URLs, or if this loader is closed, then invoking this
    * method has no effect.
    *
    * @param url the URL to be added to the search path of URLs
    */
  def addServiceURL(url: URL): Unit = addURL(url)

}
