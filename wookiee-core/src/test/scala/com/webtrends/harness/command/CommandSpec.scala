package com.webtrends.harness.command

import org.specs2.mutable.SpecificationWithJUnit

class CommandSpec extends SpecificationWithJUnit {

  "Command" should {

    "match single element paths with or without leading and trailing slashes" in {
      Command.matchPath("/v1",  "/v1") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("/v1/", "/v1") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("v1",   "/v1") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("v1/",  "/v1") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("///v1//",  "/v1") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
    }

    "match multiple element paths with or without leading and trailing slashes" in {
      Command.matchPath("/v1/foo/bar",  "/v1/foo/bar") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("/v1/foo/bar/",  "/v1/foo/bar") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("v1/foo/bar/",  "/v1/foo/bar") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("v1/foo/bar",  "/v1/foo/bar") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("////v1//foo//////bar//",  "/v1/foo/bar") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
    }

    "Not match unmatching paths" in {
      Command.matchPath("/v1",  "/v2") shouldEqual None
      Command.matchPath("/v1",  "/v10") shouldEqual None
      Command.matchPath("/v1",  "/v1/foo") shouldEqual None

      Command.matchPath("/v1/foo/bar",  "/v2/foo/bar") shouldEqual None
      Command.matchPath("/v1/foo/bar",  "/v2/foo/bar") shouldEqual None
      Command.matchPath("/v1/foo/bar",  "/v1/food/bar") shouldEqual None
      Command.matchPath("/v1/foo/bar",  "/v1/foo/bard") shouldEqual None
    }

    "Match segments containing multiple values" in {
      Command.matchPath("/v1|v2",  "/v2") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("/v1|v2",  "/v1") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))

      Command.matchPath("/v1|v2/foo|bar/baz",  "/v1/foo/baz") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("/v1|v2/foo|bar/baz",  "/v1/bar/baz") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("/v1|v2/foo|bar/baz",  "/v2/foo/baz") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
      Command.matchPath("/v1|v2/foo|bar/baz",  "/v2/bar/baz") shouldEqual Some(CommandBean(Map.empty[String, AnyRef]))
    }

    "Extract String values using $" in {
      Command.matchPath("/$ver",  "/v2") shouldEqual Some(CommandBean(Map("ver" -> "v2")))
      Command.matchPath("/$ver/foo/bar",  "/v2/foo/bar") shouldEqual Some(CommandBean(Map("ver" -> "v2")))
      Command.matchPath("/foo/$ver/bar",  "foo/v2/bar") shouldEqual Some(CommandBean(Map("ver" -> "v2")))
      Command.matchPath("/foo/bar/$ver",  "/foo/bar/v2") shouldEqual Some(CommandBean(Map("ver" -> "v2")))

      val numStr = String.valueOf(Long.MaxValue)
      Command.matchPath("/foo/bar/$ver",  "/foo/bar/" + numStr) shouldEqual Some(CommandBean(Map("ver" -> numStr)))
    }

    "Extract Integer values using $" in {
      Command.matchPath("/$ver",  "/123") shouldEqual Some(CommandBean(Map("ver" -> new Integer(123))))
      Command.matchPath("/$ver/foo/bar",  "/123/foo/bar") shouldEqual Some(CommandBean(Map("ver" -> new Integer(123))))
      Command.matchPath("/foo/$ver/bar",  "foo/123/bar") shouldEqual Some(CommandBean(Map("ver" -> new Integer(123))))
      Command.matchPath("/foo/bar/$ver",  "/foo/bar/-123") shouldEqual Some(CommandBean(Map("ver" -> new Integer(-123))))
    }

    "Extract String values using %" in {
      Command.matchPath("/%ver",  "/123") shouldEqual Some(CommandBean(Map("ver" -> "123")))
      Command.matchPath("/%ver",  "/foo") shouldEqual Some(CommandBean(Map("ver" -> "foo")))
    }

    "Extract Integer values using # and don't match if value is not an integer" in {
      Command.matchPath("/#ver",  "/123") shouldEqual Some(CommandBean(Map("ver" -> new Integer(123))))
      Command.matchPath("/#ver",  "/foo") shouldEqual None
    }
  }

}
