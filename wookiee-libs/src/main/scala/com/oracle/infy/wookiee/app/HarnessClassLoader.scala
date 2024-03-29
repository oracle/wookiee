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
package com.oracle.infy.wookiee.app

import com.oracle.infy.wookiee.service.HawkClassLoader

import java.io.InputStream
import java.net._
import scala.util.{Failure, Success, Try}

protected[oracle] class HarnessClassLoader(parent: ClassLoader) extends URLClassLoader(Array.empty[URL], parent) {
  // Holds Component and Service class loaders, the key is the 'name' of that plugin
  private var childLoaders: Map[String, HawkClassLoader] = Map()

  /**
    * Adds a sequence of urls to load into this class loader
    * @param urls Urls of the JAR files to load
    */
  def addURLs(urls: Seq[URL]): Unit = urls foreach addURL

  /**
    * Add the child service loader so it can be used to search for classes in child loaders
    * @param loader an instance of ServiceClassLoader
    */
  def addChildLoader(loader: HawkClassLoader, replace: Boolean = true): Unit = {
    if (!childLoaders.contains(loader.entityName) || replace)
      childLoaders = childLoaders + (loader.entityName -> loader)
  }

  def getChildLoaders: Seq[HawkClassLoader] = childLoaders.values.toList

  /**
    * ClassLoader overrides
    */
  override def loadClass(name: String, resolve: Boolean): Class[_] = {
    // First, check if the class has already been loaded
    Try(super.loadClass(name, resolve)) match {
      case Success(v) => v
      case Failure(_) =>
        loadClassFromChildren(name, resolve) getOrElse (throw new ClassNotFoundException(
          "Could not locate the class " + name
        ))
    }
  }

  override def getResource(name: String): URL = {
    Try(super.getResource(name)) match {
      case Success(v) if v != null => v
      case _                       => getResourceFromChildren(name)
    }
  }

  override def getResources(name: String): java.util.Enumeration[URL] = {
    super.getResources(name)
  }

  override def getResourceAsStream(name: String): InputStream = {
    super.getResourceAsStream(name)
  }

  private def loadClassFromChildren(name: String, resolve: Boolean): Option[Class[_]] = {
    if (childLoaders.isEmpty) {
      None
    } else {
      this.synchronized {
        // Get the loaded class
        childLoaders.values.filterNot(_.getLoadedClass(name).isEmpty) match {
          case Nil =>
            var ret: Option[Class[_]] = None
            childLoaders.values.find { loader =>
              ret = loader.loadClassLocally(name, resolve)
              ret.nonEmpty
            }
            ret
          case list => // Return the first if already loaded
            list.headOption.get.getLoadedClass(name)
        }
      }
    }
  }

  private def getResourceFromChildren(name: String): URL = {
    if (childLoaders.isEmpty) {
      null
    } else {
      (for {
        value <- childLoaders.values
        url = value.findResource(name)
        if url != null
      } yield url).headOption match {
        case Some(url) => url
        case None      => null
      }
    }
  }
}

protected[oracle] object HarnessClassLoader {
  def apply(parent: ClassLoader) = new HarnessClassLoader(parent)
}
