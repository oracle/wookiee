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

package com.webtrends.harness.utils

import java.io.File
import java.nio.file.{FileSystems, Files, Path}

import scala.io.Source

/**
 * @author Michael Cuthbert on 11/6/14.
 */
object FileUtil {

  /**
   * Gets the filename only for the given file, minus the extension so filename.ext will return filename
   *
   * @param f
   * @return
   */
  def getFilename(f:File) : String = {
    val name = f.getName
    name.substring(0, name.lastIndexOf('.'))
  }

  /**
   * Gets the extension only for the given file, so filename.ext will return ext
   *
   * @param f
   * @return
   */
  def getExtension(f:File) : String = {
    val name = f.getName
    name.substring(name.lastIndexOf('.') + 1)
  }

  /**
   * Gets the contents of a file as a string. Obviously don't use this with files that have the potential of
   * being large.
   *
   * @param f
   * @return
   */
  def getFileAsString(f:File) : String = {
    val src = Source.fromFile(f)
    try {
      src.mkString
    } finally {
      src.close()
    }
  }

  /**
   * Checks to see if the supplied file is a symbolic link and if it is will return
   * the real location of the file
   *
   * @param f
   * @return
   */
  def getSymLink(f:File) : File = {
    if (f == null)
      throw new NullPointerException("File must not be null")
    val path = FileSystems.getDefault.getPath(f.getPath)
    if (Files.isSymbolicLink(path)) {
      f.getCanonicalFile
    } else {
      f.getAbsoluteFile
    }
  }
}
