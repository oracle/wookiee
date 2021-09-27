package com.oracle.infy.wookiee.grpcdev.tests

object GrpcDevTestResults {

  val genProtoOptionStringResult: String = """
                                             |syntax = "proto3";
                                             |
                                             |
                                             |
                                             |// DO NOT EDIT! (this code is generated)
                                             |message GrpcTestOptionString {
                                             |  GrpcMaybeString maybeString = 1;
                                             |}
                                             |
                                             |// DO NOT EDIT! (this code is generated)
                                             |message GrpcMaybeString {
                                             |  oneof OneOf {
                                             |    string somme = 1;
                                             |    GrpcNonne nonne = 2;
                                             |  }
                                             |}
                                             |
                                             |// DO NOT EDIT! (this code is generated)
                                             |message GrpcNonne {
                                             |}
                                             |
                                             |""".stripMargin

  val genProtoOptionCaseClassResult: String = """
                                                  |syntax = "proto3";
                                                  |
                                                  |
                                                  |
                                                  |// DO NOT EDIT! (this code is generated)
                                                  |message GrpcTestOptionCaseClass {
                                                  |  GrpcMaybeTestCaseClass maybeCaseClass = 1;
                                                  |}
                                                  |
                                                  |// DO NOT EDIT! (this code is generated)
                                                  |message GrpcTestCaseClass {
                                                  |  string testString = 1;
                                                  |  int32 testInt = 2;
                                                  |}
                                                  |
                                                  |// DO NOT EDIT! (this code is generated)
                                                  |message GrpcMaybeTestCaseClass {
                                                  |  oneof OneOf {
                                                  |    GrpcTestCaseClass somme = 1;
                                                  |    GrpcNonne nonne = 2;
                                                  |  }
                                                  |}
                                                  |
                                                  |// DO NOT EDIT! (this code is generated)
                                                  |message GrpcNonne {
                                                  |}
                                                  |""".stripMargin

  val genProtoOptionOptionStringResult = """|
             |syntax = "proto3";
                                            |
                                            |
                                            |
                                            |// DO NOT EDIT! (this code is generated)
                                            |message GrpcTestOptionOptionString {
                                            |  GrpcMaybeMaybeString maybeMaybeString = 1;
                                            |}
                                            |
                                            |// DO NOT EDIT! (this code is generated)
                                            |message GrpcMaybeMaybeString {
                                            |  oneof OneOf {
                                            |    GrpcMaybeString somme = 1;
                                            |    GrpcNonne nonne = 2;
                                            |  }
                                            |}
                                            |
                                            |// DO NOT EDIT! (this code is generated)
                                            |message GrpcMaybeString {
                                            |  oneof OneOf {
                                            |    string somme = 1;
                                            |    GrpcNonne nonne = 2;
                                            |  }
                                            |}
                                            |
                                            |// DO NOT EDIT! (this code is generated)
                                            |message GrpcNonne {
                                            |}
                                            |
                                            |""".stripMargin

  val genProtoOptionOptionCaseClassResult = """|
             |syntax = "proto3";
                                                         |
                                                         |
                                                         |
                                                         |// DO NOT EDIT! (this code is generated)
                                                         |message GrpcTestOptionOptionCaseClass {
                                                         |  GrpcMaybeMaybeTestCaseClass maybeMaybeCaseClass = 1;
                                                         |}
                                                         |
                                                         |// DO NOT EDIT! (this code is generated)
                                                         |message GrpcMaybeMaybeTestCaseClass {
                                                         |  oneof OneOf {
                                                         |    GrpcMaybeTestCaseClass somme = 1;
                                                         |    GrpcNonne nonne = 2;
                                                         |  }
                                                         |}
                                                         |
                                                         |// DO NOT EDIT! (this code is generated)
                                                         |message GrpcMaybeTestCaseClass {
                                                         |  oneof OneOf {
                                                         |    GrpcTestCaseClass somme = 1;
                                                         |    GrpcNonne nonne = 2;
                                                         |  }
                                                         |}
                                                         |
                                                         |// DO NOT EDIT! (this code is generated)
                                                         |message GrpcNonne {
                                                         |}
                                                         |
                                                         |""".stripMargin

  val genScalaOptionStringResult: String =
    """
      |object implicits {
      |
      |  private def fromGrpcZonedDateTime(value: Long): Either[GrpcConversionError, ZonedDateTime] =
      |    Try {
      |      ZonedDateTime.ofInstant(Instant.ofEpochSecond(value), ZoneId.of("UTC"))
      |    }.toEither.left.map(t => GrpcConversionError(t.getMessage))
      |
      |  private def toGrpcZonedDateTime(value: ZonedDateTime): Long =
      |    value.toEpochSecond
      |  locally {
      |    val _ = (a => fromGrpcZonedDateTime(a), a => toGrpcZonedDateTime(a))
      |  }
      |
      |  implicit class TestOptionStringToGrpc(lhs: TestOptionString) {
      |
      |    def toGrpc: GrpcTestOptionString =
      |      GrpcTestOptionString(maybeString = Some(lhs.maybeString.toGrpc))
      |  }
      |
      |  implicit class TestOptionStringFromGrpc(lhs: GrpcTestOptionString) {
      |
      |    def fromGrpc: Either[GrpcConversionError, TestOptionString] =
      |      for (maybeString <- lhs.getMaybeString.fromGrpc) yield TestOptionString(maybeString = maybeString)
      |  }
      |
      |  implicit class MaybeStringToGrpc(lhs: MaybeString) {
      |
      |    def toGrpc: GrpcMaybeString = lhs match {
      |      case value: String =>
      |        GrpcMaybeString(GrpcMaybeString.OneOf.String(value.toGrpc))
      |      case value: Nonne =>
      |        GrpcMaybeString(GrpcMaybeString.OneOf.Nonne(value.toGrpc))
      |      case _ =>
      |        GrpcMaybeString(GrpcMaybeString.OneOf.Empty)
      |    }
      |  }
      |
      |  implicit class MaybeStringFromGrpc(lhs: GrpcMaybeString) {
      |
      |    def fromGrpc: Either[GrpcConversionError, MaybeString] = lhs.oneOf match {
      |      case GrpcMaybeString.OneOf.Empty =>
      |        Left(GrpcConversionError("Unable to convert object from grpc type: GrpcMaybeString"))
      |      case GrpcMaybeString.OneOf.String(value) =>
      |        value.fromGrpc
      |      case GrpcMaybeString.OneOf.Nonne(value) =>
      |        value.fromGrpc
      |    }
      |  }
      |
      |  implicit class NoneToGrpc(lhs: None) {
      |
      |    def toGrpc: GrpcNone = {
      |      val _ = lhs
      |      GrpcNone()
      |    }
      |  }
      |
      |  implicit class NoneFromGrpc(lhs: GrpcNone) {
      |
      |    def fromGrpc: Either[GrpcConversionError, None] = {
      |      val _ = lhs
      |      Right(None())
      |    }
      |  }
      |
      |  implicit class OptionStringToGrpc(lhs: Option[String]) {
      |
      |    def toGrpc: GrpcMaybeString =
      |      lhs match {
      |        case None =>
      |          GrpcMaybeString(GrpcMaybeString.OneOf.Nonne(GrpcNonne()))
      |        case Some(value) =>
      |          GrpcMaybeString(GrpcMaybeString.OneOf.Somme(value))
      |      }
      |  }
      |
      |  implicit class OptionStringFromGrpc(lhs: GrpcMaybeString) {
      |
      |    def fromGrpc: Either[GrpcConversionError, Option[String]] = lhs.oneOf match {
      |      case GrpcMaybeString.OneOf.Somme(value) =>
      |        Right(Some(value))
      |      case _ =>
      |        Right(None)
      |    }
      |  }
      |
      |}
      |
      |""".stripMargin

  val genScalaOptionCaseClassResult: String =
    """
      |object implicits {
      |
      |  private def fromGrpcZonedDateTime(value: Long): Either[GrpcConversionError, ZonedDateTime] =
      |    Try {
      |      ZonedDateTime.ofInstant(Instant.ofEpochSecond(value), ZoneId.of("UTC"))
      |    }.toEither.left.map(t => GrpcConversionError(t.getMessage))
      |
      |  private def toGrpcZonedDateTime(value: ZonedDateTime): Long =
      |    value.toEpochSecond
      |  locally {
      |    val _ = (a => fromGrpcZonedDateTime(a), a => toGrpcZonedDateTime(a))
      |  }
      |
      |  implicit class TestOptionCaseClassToGrpc(lhs: TestOptionCaseClass) {
      |
      |    def toGrpc: GrpcTestOptionCaseClass =
      |      GrpcTestOptionCaseClass(maybeCaseClass = Some(lhs.maybeCaseClass.toGrpc))
      |  }
      |
      |  implicit class TestOptionCaseClassFromGrpc(lhs: GrpcTestOptionCaseClass) {
      |
      |    def fromGrpc: Either[GrpcConversionError, TestOptionCaseClass] =
      |      for (maybeCaseClass <- lhs.getMaybeCaseClass.fromGrpc) yield TestOptionCaseClass(maybeCaseClass = maybeCaseClass)
      |  }
      |
      |  implicit class TestCaseClassToGrpc(lhs: TestCaseClass) {
      |
      |    def toGrpc: GrpcTestCaseClass =
      |      GrpcTestCaseClass(testString = lhs.testString, testInt = lhs.testInt)
      |  }
      |
      |  implicit class TestCaseClassFromGrpc(lhs: GrpcTestCaseClass) {
      |
      |    def fromGrpc: Either[GrpcConversionError, TestCaseClass] =
      |      for {
      |        testString <- Right(lhs.testString)
      |        testInt <- Right(lhs.testInt)
      |      } yield TestCaseClass(testString = testString, testInt = testInt)
      |  }
      |
      |  implicit class MaybeTestCaseClassToGrpc(lhs: MaybeTestCaseClass) {
      |
      |    def toGrpc: GrpcMaybeTestCaseClass = lhs match {
      |      case value: TestCaseClass =>
      |        GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.TestCaseClass(value.toGrpc))
      |      case value: Nonne =>
      |        GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.Nonne(value.toGrpc))
      |      case _ =>
      |        GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.Empty)
      |    }
      |  }
      |
      |  implicit class MaybeTestCaseClassFromGrpc(lhs: GrpcMaybeTestCaseClass) {
      |
      |    def fromGrpc: Either[GrpcConversionError, MaybeTestCaseClass] = lhs.oneOf match {
      |      case GrpcMaybeTestCaseClass.OneOf.Empty =>
      |        Left(GrpcConversionError("Unable to convert object from grpc type: GrpcMaybeTestCaseClass"))
      |      case GrpcMaybeTestCaseClass.OneOf.TestCaseClass(value) =>
      |        value.fromGrpc
      |      case GrpcMaybeTestCaseClass.OneOf.Nonne(value) =>
      |        value.fromGrpc
      |    }
      |  }
      |
      |  implicit class NoneToGrpc(lhs: None) {
      |
      |    def toGrpc: GrpcNone = {
      |      val _ = lhs
      |      GrpcNone()
      |    }
      |  }
      |
      |  implicit class NoneFromGrpc(lhs: GrpcNone) {
      |
      |    def fromGrpc: Either[GrpcConversionError, None] = {
      |      val _ = lhs
      |      Right(None())
      |    }
      |  }
      |
      |  implicit class OptionTestCaseClassToGrpc(lhs: Option[TestCaseClass]) {
      |
      |    def toGrpc: GrpcMaybeTestCaseClass =
      |      lhs match {
      |        case None =>
      |          GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.Nonne(GrpcNonne()))
      |        case Some(value) =>
      |          GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.Somme(value.toGrpc))
      |      }
      |  }
      |
      |  implicit class OptionTestCaseClassFromGrpc(lhs: GrpcMaybeTestCaseClass) {
      |
      |    def fromGrpc: Either[GrpcConversionError, Option[TestCaseClass]] = lhs.oneOf match {
      |      case GrpcMaybeTestCaseClass.OneOf.Somme(value) =>
      |        value.fromGrpc.map(Some(_))
      |      case _ =>
      |        Right(None)
      |    }
      |  }
      |
      |}
      |
      |""".stripMargin

  val genScalaOptionOptionStringResult =
    """
      |object implicits {
      |
      |  private def fromGrpcZonedDateTime(value: Long): Either[GrpcConversionError, ZonedDateTime] =
      |    Try {
      |      ZonedDateTime.ofInstant(Instant.ofEpochSecond(value), ZoneId.of("UTC"))
      |    }.toEither.left.map(t => GrpcConversionError(t.getMessage))
      |
      |  private def toGrpcZonedDateTime(value: ZonedDateTime): Long =
      |    value.toEpochSecond
      |  locally {
      |    val _ = (a => fromGrpcZonedDateTime(a), a => toGrpcZonedDateTime(a))
      |  }
      |
      |  implicit class TestOptionOptionStringToGrpc(lhs: TestOptionOptionString) {
      |
      |    def toGrpc: GrpcTestOptionOptionString =
      |      GrpcTestOptionOptionString(maybeMaybeString = Some(lhs.maybeMaybeString.toGrpc))
      |  }
      |
      |  implicit class TestOptionOptionStringFromGrpc(lhs: GrpcTestOptionOptionString) {
      |
      |    def fromGrpc: Either[GrpcConversionError, TestOptionOptionString] =
      |      for (maybeMaybeString <- lhs.getMaybeMaybeString.fromGrpc)
      |        yield TestOptionOptionString(maybeMaybeString = maybeMaybeString)
      |  }
      |
      |  implicit class MaybeMaybeStringToGrpc(lhs: MaybeMaybeString) {
      |
      |    def toGrpc: GrpcMaybeMaybeString = lhs match {
      |      case value: MaybeString =>
      |        GrpcMaybeMaybeString(GrpcMaybeMaybeString.OneOf.MaybeString(value.toGrpc))
      |      case value: Nonne =>
      |        GrpcMaybeMaybeString(GrpcMaybeMaybeString.OneOf.Nonne(value.toGrpc))
      |      case _ =>
      |        GrpcMaybeMaybeString(GrpcMaybeMaybeString.OneOf.Empty)
      |    }
      |  }
      |
      |  implicit class MaybeMaybeStringFromGrpc(lhs: GrpcMaybeMaybeString) {
      |
      |    def fromGrpc: Either[GrpcConversionError, MaybeMaybeString] = lhs.oneOf match {
      |      case GrpcMaybeMaybeString.OneOf.Empty =>
      |        Left(GrpcConversionError("Unable to convert object from grpc type: GrpcMaybeMaybeString"))
      |      case GrpcMaybeMaybeString.OneOf.MaybeString(value) =>
      |        value.fromGrpc
      |      case GrpcMaybeMaybeString.OneOf.Nonne(value) =>
      |        value.fromGrpc
      |    }
      |  }
      |
      |  implicit class MaybeStringToGrpc(lhs: MaybeString) {
      |
      |    def toGrpc: GrpcMaybeString = lhs match {
      |      case value: String =>
      |        GrpcMaybeString(GrpcMaybeString.OneOf.String(value.toGrpc))
      |      case value: Nonne =>
      |        GrpcMaybeString(GrpcMaybeString.OneOf.Nonne(value.toGrpc))
      |      case _ =>
      |        GrpcMaybeString(GrpcMaybeString.OneOf.Empty)
      |    }
      |  }
      |
      |  implicit class MaybeStringFromGrpc(lhs: GrpcMaybeString) {
      |
      |    def fromGrpc: Either[GrpcConversionError, MaybeString] = lhs.oneOf match {
      |      case GrpcMaybeString.OneOf.Empty =>
      |        Left(GrpcConversionError("Unable to convert object from grpc type: GrpcMaybeString"))
      |      case GrpcMaybeString.OneOf.String(value) =>
      |        value.fromGrpc
      |      case GrpcMaybeString.OneOf.Nonne(value) =>
      |        value.fromGrpc
      |    }
      |  }
      |
      |  implicit class NoneToGrpc(lhs: None) {
      |
      |    def toGrpc: GrpcNone = {
      |      val _ = lhs
      |      GrpcNone()
      |    }
      |  }
      |
      |  implicit class NoneFromGrpc(lhs: GrpcNone) {
      |
      |    def fromGrpc: Either[GrpcConversionError, None] = {
      |      val _ = lhs
      |      Right(None())
      |    }
      |  }
      |
      |  implicit class OptionStringToGrpc(lhs: Option[String]) {
      |
      |    def toGrpc: GrpcMaybeString =
      |      lhs match {
      |        case None =>
      |          GrpcMaybeString(GrpcMaybeString.OneOf.Nonne(GrpcNonne()))
      |        case Some(value) =>
      |          GrpcMaybeString(GrpcMaybeString.OneOf.Somme(value))
      |      }
      |  }
      |
      |  implicit class OptionStringFromGrpc(lhs: GrpcMaybeString) {
      |
      |    def fromGrpc: Either[GrpcConversionError, Option[String]] = lhs.oneOf match {
      |      case GrpcMaybeString.OneOf.Somme(value) =>
      |        Right(Some(value))
      |      case _ =>
      |        Right(None)
      |    }
      |  }
      |
      |  implicit class OptionOptionStringToGrpc(lhs: Option[Option[String]]) {
      |
      |    def toGrpc: GrpcMaybeMaybeString =
      |      lhs match {
      |        case None =>
      |          GrpcMaybeMaybeString(GrpcMaybeMaybeString.OneOf.Nonne(GrpcNonne()))
      |        case Some(value) =>
      |          GrpcMaybeMaybeString(GrpcMaybeMaybeString.OneOf.Somme(value.toGrpc))
      |      }
      |  }
      |
      |  implicit class OptionOptionStringFromGrpc(lhs: GrpcMaybeMaybeString) {
      |
      |    def fromGrpc: Either[GrpcConversionError, Option[Option[String]]] = lhs.oneOf match {
      |      case GrpcMaybeMaybeString.OneOf.Somme(value) =>
      |        value.fromGrpc.map(Some(_))
      |      case _ =>
      |        Right(None)
      |    }
      |  }
      |
      |}
      |""".stripMargin

  val genScalaOptionOptionCaseClassResult =
    """
      |object implicits {
      |
      |  private def fromGrpcZonedDateTime(value: Long): Either[GrpcConversionError, ZonedDateTime] =
      |    Try {
      |      ZonedDateTime.ofInstant(Instant.ofEpochSecond(value), ZoneId.of("UTC"))
      |    }.toEither.left.map(t => GrpcConversionError(t.getMessage))
      |
      |  private def toGrpcZonedDateTime(value: ZonedDateTime): Long =
      |    value.toEpochSecond
      |  locally {
      |    val _ = (a => fromGrpcZonedDateTime(a), a => toGrpcZonedDateTime(a))
      |  }
      |
      |  implicit class TestOptionOptionCaseClassToGrpc(lhs: TestOptionOptionCaseClass) {
      |
      |    def toGrpc: GrpcTestOptionOptionCaseClass =
      |      GrpcTestOptionOptionCaseClass(maybeMaybeCaseClass = Some(lhs.maybeMaybeCaseClass.toGrpc))
      |  }
      |
      |  implicit class TestOptionOptionCaseClassFromGrpc(lhs: GrpcTestOptionOptionCaseClass) {
      |
      |    def fromGrpc: Either[GrpcConversionError, TestOptionOptionCaseClass] =
      |      for (maybeMaybeCaseClass <- lhs.getMaybeMaybeCaseClass.fromGrpc)
      |        yield TestOptionOptionCaseClass(maybeMaybeCaseClass = maybeMaybeCaseClass)
      |  }
      |
      |  implicit class MaybeMaybeTestCaseClassToGrpc(lhs: MaybeMaybeTestCaseClass) {
      |
      |    def toGrpc: GrpcMaybeMaybeTestCaseClass = lhs match {
      |      case value: MaybeTestCaseClass =>
      |        GrpcMaybeMaybeTestCaseClass(GrpcMaybeMaybeTestCaseClass.OneOf.MaybeTestCaseClass(value.toGrpc))
      |      case value: Nonne =>
      |        GrpcMaybeMaybeTestCaseClass(GrpcMaybeMaybeTestCaseClass.OneOf.Nonne(value.toGrpc))
      |      case _ =>
      |        GrpcMaybeMaybeTestCaseClass(GrpcMaybeMaybeTestCaseClass.OneOf.Empty)
      |    }
      |  }
      |
      |  implicit class MaybeMaybeTestCaseClassFromGrpc(lhs: GrpcMaybeMaybeTestCaseClass) {
      |
      |    def fromGrpc: Either[GrpcConversionError, MaybeMaybeTestCaseClass] = lhs.oneOf match {
      |      case GrpcMaybeMaybeTestCaseClass.OneOf.Empty =>
      |        Left(GrpcConversionError("Unable to convert object from grpc type: GrpcMaybeMaybeTestCaseClass"))
      |      case GrpcMaybeMaybeTestCaseClass.OneOf.MaybeTestCaseClass(value) =>
      |        value.fromGrpc
      |      case GrpcMaybeMaybeTestCaseClass.OneOf.Nonne(value) =>
      |        value.fromGrpc
      |    }
      |  }
      |
      |  implicit class MaybeTestCaseClassToGrpc(lhs: MaybeTestCaseClass) {
      |
      |    def toGrpc: GrpcMaybeTestCaseClass = lhs match {
      |      case value: TestCaseClass =>
      |        GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.TestCaseClass(value.toGrpc))
      |      case value: Nonne =>
      |        GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.Nonne(value.toGrpc))
      |      case _ =>
      |        GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.Empty)
      |    }
      |  }
      |
      |  implicit class MaybeTestCaseClassFromGrpc(lhs: GrpcMaybeTestCaseClass) {
      |
      |    def fromGrpc: Either[GrpcConversionError, MaybeTestCaseClass] = lhs.oneOf match {
      |      case GrpcMaybeTestCaseClass.OneOf.Empty =>
      |        Left(GrpcConversionError("Unable to convert object from grpc type: GrpcMaybeTestCaseClass"))
      |      case GrpcMaybeTestCaseClass.OneOf.TestCaseClass(value) =>
      |        value.fromGrpc
      |      case GrpcMaybeTestCaseClass.OneOf.Nonne(value) =>
      |        value.fromGrpc
      |    }
      |  }
      |
      |  implicit class NoneToGrpc(lhs: None) {
      |
      |    def toGrpc: GrpcNone = {
      |      val _ = lhs
      |      GrpcNone()
      |    }
      |  }
      |
      |  implicit class NoneFromGrpc(lhs: GrpcNone) {
      |
      |    def fromGrpc: Either[GrpcConversionError, None] = {
      |      val _ = lhs
      |      Right(None())
      |    }
      |  }
      |
      |  implicit class OptionTestCaseClassToGrpc(lhs: Option[TestCaseClass]) {
      |
      |    def toGrpc: GrpcMaybeTestCaseClass =
      |      lhs match {
      |        case None =>
      |          GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.Nonne(GrpcNonne()))
      |        case Some(value) =>
      |          GrpcMaybeTestCaseClass(GrpcMaybeTestCaseClass.OneOf.Somme(value.toGrpc))
      |      }
      |  }
      |
      |  implicit class OptionTestCaseClassFromGrpc(lhs: GrpcMaybeTestCaseClass) {
      |
      |    def fromGrpc: Either[GrpcConversionError, Option[TestCaseClass]] = lhs.oneOf match {
      |      case GrpcMaybeTestCaseClass.OneOf.Somme(value) =>
      |        value.fromGrpc.map(Some(_))
      |      case _ =>
      |        Right(None)
      |    }
      |  }
      |
      |  implicit class OptionOptionTestCaseClassToGrpc(lhs: Option[Option[TestCaseClass]]) {
      |
      |    def toGrpc: GrpcMaybeMaybeTestCaseClass =
      |      lhs match {
      |        case None =>
      |          GrpcMaybeMaybeTestCaseClass(GrpcMaybeMaybeTestCaseClass.OneOf.Nonne(GrpcNonne()))
      |        case Some(value) =>
      |          GrpcMaybeMaybeTestCaseClass(GrpcMaybeMaybeTestCaseClass.OneOf.Somme(value.toGrpc))
      |      }
      |  }
      |
      |  implicit class OptionOptionTestCaseClassFromGrpc(lhs: GrpcMaybeMaybeTestCaseClass) {
      |
      |    def fromGrpc: Either[GrpcConversionError, Option[Option[TestCaseClass]]] = lhs.oneOf match {
      |      case GrpcMaybeMaybeTestCaseClass.OneOf.Somme(value) =>
      |        value.fromGrpc.map(Some(_))
      |      case _ =>
      |        Right(None)
      |    }
      |  }
      |
      |}
      |
      |""".stripMargin
}
