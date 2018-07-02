package com.webtrends.harness.utils

import java.util.Locale

import org.specs2.mutable.SpecificationWithJUnit

class LocalizedStringSpec extends SpecificationWithJUnit {

    "localized message" should {
      LocalizedString("hello")(Locale.ENGLISH) must be equalTo "Hello"
      LocalizedString("hello")(Locale.forLanguageTag("ru"))  must be equalTo "Привет"
    }
    "fallback to default" should {
      LocalizedString("world")(Locale.ENGLISH) must be equalTo "World"
      LocalizedString("world")(Locale.forLanguageTag("ru")) must be equalTo "World"
    }
    "format" should {
      LocalizedString("greet", "world")(Locale.ENGLISH) must be equalTo "Hello, world"
      LocalizedString("greet", "world")(Locale.forLanguageTag("ru")) must be equalTo "Привет, world"
    }

  "localized message in custom path" should {
    LocalizedString("custom_path.hello")(Locale.ENGLISH, "com.custom.path.messages") must be equalTo "Hello"
    LocalizedString("custom_path.hello")(Locale.forLanguageTag("ru"), "com.custom.path.messages")  must be equalTo "Привет"
  }
  "fallback to default in custom path" should {
    LocalizedString("custom_path.world")(Locale.ENGLISH, "com.custom.path.messages") must be equalTo "World"
    LocalizedString("custom_path.world")(Locale.forLanguageTag("ru"), "com.custom.path.messages") must be equalTo "World"
  }
  "format in custom path" should {
    LocalizedString("custom_path.greet", "world")(Locale.ENGLISH, "com.custom.path.messages") must be equalTo "Hello, world"
    LocalizedString("custom_path.greet", "world")(Locale.forLanguageTag("ru"), "com.custom.path.messages") must be equalTo "Привет, world"
  }


  "custom localized message" should {
    LocalizedString("custom.hello")(Locale.ENGLISH, "custom") must be equalTo "Hello"
    LocalizedString("custom.hello")(Locale.forLanguageTag("ru"), "custom")  must be equalTo "Привет"
  }
  "custom fallback to default" should {
    LocalizedString("custom.world")(Locale.ENGLISH, "custom") must be equalTo "World"
    LocalizedString("custom.world")(Locale.forLanguageTag("ru"), "custom") must be equalTo "World"
  }
  "custom format" should {
    LocalizedString("custom.greet", "custom world")(Locale.ENGLISH, "custom") must be equalTo "Hello, custom world"
    LocalizedString("custom.greet", "custom world")(Locale.forLanguageTag("ru"), "custom") must be equalTo "Привет, custom world"
  }
}