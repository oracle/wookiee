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

package com.webtrends.harness.utils

import java.text.MessageFormat
import java.util.{Locale, ResourceBundle}

import scala.collection.mutable

/** Messages externalization
  *
  * == Overview ==
  * You would use it like so:
  *
  * {{{
  * Localized(user) { implicit lang =>
  *   val error = LocalizedString("error")
  * }
  * }}}
  *
  * Messages are stored in `messages_XXX.properties` files in UTF-8 encoding in resources.
  * The lookup will fallback to default file `messages.properties` if the string is not found in
  * the language-specific file.
  *
  * Messages are formatted with `java.text.MessageFormat`.
  */
trait LocalizedString {
  /** get the message w/o formatting */
  def raw(msg: String)(implicit locale: Locale = Locale.getDefault, context: String = "messages"): String = {
    val bundle = ResourceBundle.getBundle(context, locale, new UTF8BundleControl(mutable.Queue.empty))
    bundle.getString(msg)
  }

  def apply(msg: String, args: Any*)(locale: Locale = Locale.getDefault, context: String = "messages"): String = {
    new MessageFormat(raw(msg)(locale, context), locale).format(args.map(_.asInstanceOf[java.lang.Object]).toArray)
  }
}


object LocalizedString extends LocalizedString


// @see https://gist.github.com/alaz/1388917
// @see http://stackoverflow.com/questions/4659929/how-to-use-utf-8-in-resource-properties-with-resourcebundle
private[utils] class UTF8BundleControl(fallBackLocales: mutable.Queue[Locale]) extends ResourceBundle.Control {

  val Format = "properties.utf8"

  override def getFormats(baseName: String): java.util.List[String] = {
    import scala.collection.JavaConverters._

    Seq(Format).asJava
  }

  override def getFallbackLocale(baseName: String, locale: Locale): Locale = {
    val defaultLocale = Locale.getDefault
    (fallBackLocales.isEmpty, locale) match {
      case (true, `defaultLocale`) => null
      case (true, _) => defaultLocale
      case (false, _) => fallBackLocales.dequeue()
    }
  }

  override def newBundle(baseName: String, locale: Locale, fmt: String, loader: ClassLoader, reload: Boolean): ResourceBundle = {
    import java.util.PropertyResourceBundle
    import java.io.InputStreamReader

    // The below is an approximate copy of the default Java implementation
    def resourceName = toResourceName(toBundleName(baseName, locale), "properties")

    def stream =
      if (reload) {
        for {url <- Option(loader getResource resourceName)
             connection <- Option(url.openConnection)}
          yield {
            connection.setUseCaches(false)
            connection.getInputStream
          }
      } else
        Option(loader getResourceAsStream resourceName)

    (for {format <- Option(fmt) if format == Format
          is <- stream}
      yield new PropertyResourceBundle(new InputStreamReader(is, "UTF-8"))).orNull
  }
}

/**
 * Support for multiple locales in Localization
 * It accepts ordered collection of locales
 * returns Localized value in highest locale that App supports
 * */
case class LocalizableString(key: String, args: Seq[Any] = Nil, context: String = "messages") {

  private def resourceBundle(key: String)(locale: Locale = Locale.getDefault, context: String, fallbackLocales: Seq[Locale] = Nil) = {
  ResourceBundle.getBundle(context, locale, new UTF8BundleControl(mutable.Queue(fallbackLocales: _*)))
  }

  def localize(locales: Seq[Locale] = Seq(Locale.getDefault)): String = {
    val bundle = locales match {
      case Nil => resourceBundle(key)(Locale.getDefault, context)
      case head :: tail => resourceBundle(key)(head, context, tail)
    }
    new MessageFormat(bundle.getString(key), bundle.getLocale).format(args.map(_.asInstanceOf[java.lang.Object]).toArray)
  }
}