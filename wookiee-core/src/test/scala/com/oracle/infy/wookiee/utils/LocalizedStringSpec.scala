package com.oracle.infy.wookiee.utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Locale

class LocalizedStringSpec extends AnyWordSpecLike with Matchers {
  val WORLD: String = "world"

  "Localization" should {
    "localized message" in {
      LocalizedString("hello")(Locale.ENGLISH) shouldBe "Hello"
      LocalizedString("hello")(Locale.forLanguageTag("ru")) shouldBe "Привет"
    }
    "fallback to default" in {
      LocalizedString(WORLD)(Locale.ENGLISH) shouldBe "World"
      LocalizedString(WORLD)(Locale.forLanguageTag("ru")) shouldBe "World"
    }
    "format" in {
      LocalizedString("greet", WORLD)(Locale.ENGLISH) shouldBe "Hello, world"
      LocalizedString("greet", WORLD)(Locale.forLanguageTag("ru")) shouldBe "Привет, world"
    }

    "localized message in custom path" in {
      LocalizedString("custom_path.hello")(Locale.ENGLISH, "com.custom.path.messages") shouldBe "Hello"
      LocalizedString("custom_path.hello")(Locale.forLanguageTag("ru"), "com.custom.path.messages") shouldBe "Привет"
    }

    "fallback to default in custom path" in {
      LocalizedString("custom_path.world")(Locale.ENGLISH, "com.custom.path.messages") shouldBe "World"
      LocalizedString("custom_path.world")(Locale.forLanguageTag("ru"), "com.custom.path.messages") shouldBe "World"
    }
    "format in custom path" in {
      LocalizedString("custom_path.greet", WORLD)(Locale.ENGLISH, "com.custom.path.messages") shouldBe "Hello, world"
      LocalizedString("custom_path.greet", WORLD)(Locale.forLanguageTag("ru"), "com.custom.path.messages") shouldBe "Привет, world"
    }

    "custom localized message" in {
      LocalizedString("custom.hello")(Locale.ENGLISH, "custom") shouldBe "Hello"
      LocalizedString("custom.hello")(Locale.forLanguageTag("ru"), "custom") shouldBe "Привет"
    }
    "custom fallback to default" in {
      LocalizedString("custom.world")(Locale.ENGLISH, "custom") shouldBe "World"
      LocalizedString("custom.world")(Locale.forLanguageTag("ru"), "custom") shouldBe "World"
    }
    "custom format" in {
      LocalizedString("custom.greet", "custom world")(Locale.ENGLISH, "custom") shouldBe "Hello, custom world"
      LocalizedString("custom.greet", "custom world")(Locale.forLanguageTag("ru"), "custom") shouldBe "Привет, custom world"
    }
  }

  "Localizable" should {
    case class HelloMessage() extends Localizable {
      val key = "hello"
      val context: String = "messages"
    }
    case class GreetMessage(override val args: Seq[Any]) extends Localizable {
      val key = "custom.greet"
      val context: String = "custom"
    }
    "localize message" in {
      HelloMessage().localize(Seq(Locale.ENGLISH)) shouldBe "Hello"
      HelloMessage().localize(Seq(Locale.FRENCH, Locale.forLanguageTag("ru"), Locale.ENGLISH)) shouldBe "Привет"
    }
    "custom path with localizable argument" in {
      GreetMessage(Seq(HelloMessage())).localize(Seq(Locale.ENGLISH)) shouldBe "Hello, Hello"
      GreetMessage(Seq(HelloMessage()))
        .localize(Seq(Locale.FRENCH, Locale.forLanguageTag("ru"), Locale.ENGLISH)) shouldBe "Привет, Привет"
    }
  }
}
