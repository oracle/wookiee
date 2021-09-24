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
