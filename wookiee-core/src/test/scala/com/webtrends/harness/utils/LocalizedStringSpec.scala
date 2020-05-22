package com.webtrends.harness.utils

import java.util.Locale

import org.scalatest.{MustMatchers, WordSpecLike}

class LocalizedStringSpec extends WordSpecLike with MustMatchers {
  "Localization" should {
    "localized message" in {
      LocalizedString("hello")(Locale.ENGLISH) mustBe "Hello"
      LocalizedString("hello")(Locale.forLanguageTag("ru")) mustBe "Привет"
    }

    "fallback to default" in {
      LocalizedString("world")(Locale.ENGLISH) mustBe "World"
      LocalizedString("world")(Locale.forLanguageTag("ru")) mustBe "World"
    }
    "format" in {
      LocalizedString("greet", "world")(Locale.ENGLISH) mustBe "Hello, world"
      LocalizedString("greet", "world")(Locale.forLanguageTag("ru")) mustBe "Привет, world"
    }

    "localized message in custom path" in {
      LocalizedString("custom_path.hello")(Locale.ENGLISH, "com.custom.path.messages") mustBe "Hello"
      LocalizedString("custom_path.hello")(Locale.forLanguageTag("ru"), "com.custom.path.messages") mustBe "Привет"
    }

    "fallback to default in custom path" in {
      LocalizedString("custom_path.world")(Locale.ENGLISH, "com.custom.path.messages") mustBe "World"
      LocalizedString("custom_path.world")(Locale.forLanguageTag("ru"), "com.custom.path.messages") mustBe "World"
    }
    "format in custom path" in {
      LocalizedString("custom_path.greet", "world")(Locale.ENGLISH, "com.custom.path.messages") mustBe "Hello, world"
      LocalizedString("custom_path.greet", "world")(Locale.forLanguageTag("ru"), "com.custom.path.messages") mustBe "Привет, world"
    }


    "custom localized message" in {
      LocalizedString("custom.hello")(Locale.ENGLISH, "custom") mustBe "Hello"
      LocalizedString("custom.hello")(Locale.forLanguageTag("ru"), "custom") mustBe "Привет"
    }
    "custom fallback to default" in {
      LocalizedString("custom.world")(Locale.ENGLISH, "custom") mustBe "World"
      LocalizedString("custom.world")(Locale.forLanguageTag("ru"), "custom") mustBe "World"
    }
    "custom format" in {
      LocalizedString("custom.greet", "custom world")(Locale.ENGLISH, "custom") mustBe "Hello, custom world"
      LocalizedString("custom.greet", "custom world")(Locale.forLanguageTag("ru"), "custom") mustBe "Привет, custom world"
    }
  }

  "Localizable"  should {
    case class HelloMessage() extends Localizable {
      val key = "hello"
      val context: String = "messages"
    }
    case class GreetMessage(override val args: Seq[Any]) extends Localizable {
      val key = "custom.greet"
      val context: String = "custom"
    }
    "localize message" in {
      HelloMessage().localize(Seq(Locale.ENGLISH)) mustBe "Hello"
      HelloMessage().localize(Seq(Locale.FRENCH, Locale.forLanguageTag("ru"), Locale.ENGLISH)) mustBe "Привет"
    }
    "custom path with localizable argument" in {
      GreetMessage(Seq(HelloMessage())).localize(Seq(Locale.ENGLISH)) mustBe "Hello, Hello"
      GreetMessage(Seq(HelloMessage())).localize(Seq(Locale.FRENCH, Locale.forLanguageTag("ru"), Locale.ENGLISH)) mustBe "Привет, Привет"
    }
  }
}