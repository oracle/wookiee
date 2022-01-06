package com.oracle.infy.wookiee.utils

import java.util.Locale

/**
 * Support for multiple locales in Localization
 * Arguments can be converted to Localized value when argument is of type Localizable
 * It accepts ordered collection of locales
 * returns Localized value in highest locale that App supports
 * */
trait Localizable {
  def key: String

  def args: Seq[Any] = Nil

  def context: String

  def localize(locales: Seq[Locale]): String = {
    val localizedArgs = args.map {
      case l: Localizable => l.localize(locales)
      case a => a
    }
    LocalizableString(key, localizedArgs, context).localize(locales)
  }
}
