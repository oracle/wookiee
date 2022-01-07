package com.oracle.infy.wookiee.utils

import java.io.File
import java.nio.file.{FileSystems, Files}
import scala.io.Source

/**
  * @author Michael Cuthbert on 11/6/14.
  */
object FileUtil {

  /**
    * Gets the filename only for the given file, minus the extension so filename.ext will return filename
    */
  def getFilename(f: File): String = {
    val name = f.getName
    name.substring(0, name.lastIndexOf('.'))
  }

  /**
    * Gets the extension only for the given file, so filename.ext will return ext
    */
  def getExtension(f: File): String = {
    val name = f.getName
    name.substring(name.lastIndexOf('.') + 1)
  }

  /**
    * Gets the contents of a file as a string. Obviously don't use this with files that have the potential of
    * being large.
    */
  def getFileAsString(f: File): String = {
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
    */
  def getSymLink(f: File): File = {
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
