package com.webtrends.harness.command

import org.specs2.mutable.SpecificationWithJUnit

class CommandSpec extends SpecificationWithJUnit {
  
//  "Command" should {
//
//    "match single element paths with or without leading and trailing slashes" in {
//      val bean = CommandBean[Map[String, AnyRef]](Map.empty[String, AnyRef])
//
//      Command.matchPath("/v1",  "/v1", bean) shouldEqual Some(bean)
//      Command.matchPath("/v1/", "/v1", bean) shouldEqual Some(bean)
//      Command.matchPath("v1",   "/v1", bean) shouldEqual Some(bean)
//      Command.matchPath("v1/",  "/v1", bean) shouldEqual Some(bean)
//      Command.matchPath("///v1//",  "/v1", bean) shouldEqual Some(bean)
//    }
//
//    "match single element paths regardless of case" in {
//      val bean = CommandBean[Map[String, AnyRef]](Map.empty[String, AnyRef])
//      Command.matchPath("/v1/foo/bar",  "/v1/foo/bar", bean) shouldEqual Some(bean)
//      Command.matchPath("/V1/Foo/BAr",  "/v1/foo/bar", bean) shouldEqual Some(bean)
//      Command.matchPath("/v1/foO/baR",  "/V1/fOo/bAr", bean) shouldEqual Some(bean)
//    }
//
//    "match multiple element paths with or without leading and trailing slashes" in {
//      val bean = CommandBean[Map[String, AnyRef]](Map.empty[String, AnyRef])
//      Command.matchPath("/v1/foo/bar",  "/v1/foo/bar", bean) shouldEqual Some(bean)
//      Command.matchPath("/v1/foo/bar/",  "/v1/foo/bar", bean) shouldEqual Some(bean)
//      Command.matchPath("v1/foo/bar/",  "/v1/foo/bar", bean) shouldEqual Some(bean)
//      Command.matchPath("v1/foo/bar",  "/v1/foo/bar", bean) shouldEqual Some(bean)
//      Command.matchPath("////v1//foo//////bar//",  "/v1/foo/bar", bean) shouldEqual Some(bean)
//    }
//
//    "Not match unmatching paths" in {
//      val bean = CommandBean[Map[String, AnyRef]](Map.empty[String, AnyRef])
//      Command.matchPath("/v1",  "/v2", bean) shouldEqual None
//      Command.matchPath("/v1",  "/v10", bean) shouldEqual None
//      Command.matchPath("/v1",  "/v1/foo", bean) shouldEqual None
//
//      Command.matchPath("/v1/foo/bar",  "/v2/foo/bar", bean) shouldEqual None
//      Command.matchPath("/v1/foo/bar",  "/v2/foo/bar", bean) shouldEqual None
//      Command.matchPath("/v1/foo/bar",  "/v1/food/bar", bean) shouldEqual None
//      Command.matchPath("/v1/foo/bar",  "/v1/foo/bard", bean) shouldEqual None
//    }
//
//    "Match segments containing multiple values" in {
//      val bean = CommandBean[Map[String, AnyRef]](Map.empty[String, AnyRef])
//      Command.matchPath("/v1|v2",  "/v2", bean) shouldEqual Some(bean)
//      Command.matchPath("/v1|v2",  "/v1", bean) shouldEqual Some(bean)
//
//      Command.matchPath("/v1|v2/foo|bar/baz",  "/v1/foo/baz", bean) shouldEqual Some(bean)
//      Command.matchPath("/v1|v2/foo|bar/baz",  "/v1/bar/baz", bean) shouldEqual Some(bean)
//      Command.matchPath("/v1|v2/foo|bar/baz",  "/v2/foo/baz", bean) shouldEqual Some(bean)
//      Command.matchPath("/v1|v2/foo|bar/baz",  "/v2/bar/baz", bean) shouldEqual Some(bean)
//      Command.matchPath("/v1|v2/foo|bar/baz",  "/v2/bar/BAZ", bean) shouldEqual Some(bean)
//    }
//
//    "Extract String values using $" in {
//      val bean = CommandBean[Map[String, AnyRef]](Map("ver" -> "v2"))
//      Command.matchPath("/$ver",  "/v2", bean) shouldEqual Some(bean)
//      Command.matchPath("/$ver/foo/bar",  "/v2/foo/bar", bean) shouldEqual Some(bean)
//      Command.matchPath("/foo/$ver/bar",  "foo/v2/bar", bean) shouldEqual Some(bean)
//      Command.matchPath("/foo/bar/$ver",  "/foo/bar/v2", bean) shouldEqual Some(bean)
//
//      val numStr = String.valueOf(Long.MaxValue)
//      Command.matchPath("/foo/bar/$ver",  "/foo/bar/" + numStr, bean) shouldEqual Some(bean)
//    }
//
//    "Extract Integer values using $" in {
//      val bean = CommandBean[Map[String, AnyRef]](Map("ver" -> new Integer(123)))
//      Command.matchPath("/$ver",  "/123", bean) shouldEqual Some(bean)
//      Command.matchPath("/$ver/foo/bar",  "/123/foo/bar",bean) shouldEqual Some(bean)
//      Command.matchPath("/foo/$ver/bar",  "foo/123/bar", bean) shouldEqual Some(bean)
//      Command.matchPath("/foo/bar/$ver",  "/foo/bar/-123", bean) shouldEqual Some(CommandBean[Map[String, AnyRef]](Map("ver" -> new Integer(-123))))
//    }
//
//    "Extract String values using %" in {
//      Command.matchPath("/%ver",  "/123", CommandBean[Map[String, AnyRef]](Map("ver" -> "123"))) shouldEqual Some(CommandBean[Map[String, AnyRef]](Map("ver" -> "123")))
//      Command.matchPath("/%ver",  "/foo", CommandBean[Map[String, AnyRef]](Map("ver" -> "foo"))) shouldEqual Some(CommandBean[Map[String, AnyRef]](Map("ver" -> "foo")))
//    }
//
//    "Extract Integer values using # and don't match if value is not an integer" in {
//      Command.matchPath("/#ver",  "/123", CommandBean[Map[String, AnyRef]](Map("ver" -> new Integer(123)))) shouldEqual Some(CommandBean[Map[String, AnyRef]](Map("ver" -> new Integer(123))))
//      Command.matchPath("/#ver",  "/foo", CommandBean[Map[String, AnyRef]](Map.empty)) shouldEqual None
//    }
//  }

}
