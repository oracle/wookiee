package com.webtrends.harness.command

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.specs2.mutable.SpecificationWithJUnit
import java.lang.Double

class CommandBeanExtractionSpec extends SpecificationWithJUnit
{

  def passThroughFac(v: Map[String, Any]): Map[String, Any] = v

  "BeanExtractionSpec" in {

    "extractBeanParameters" should {

      "Extract required String parameter" in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            RequiredStringCommandBeanExtractParameter("requiredString")
          )
        }

        val bean = new MapCommandBean(Map(
          "requiredString" -> "requiredValue"
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beSuccessfulTry

        extractPromise.get("requiredString") mustEqual "requiredValue"
      }

      "Extract optional String parameter" in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            OptionalStringCommandBeanExtractParameter("optionalString", Some(() => "defaultValue"))
          )
        }

        val bean = new MapCommandBean( Map(
          "optionalString" -> "optionalValue"
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beSuccessfulTry

        extractPromise.get("optionalString") mustEqual "optionalValue"
      }

      "Fall back to default value if optional String parameter is not found " in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            OptionalStringCommandBeanExtractParameter("optionalString", Some(() => "defaultValue"))
          )
        }

        val bean = new MapCommandBean(Map())

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beSuccessfulTry

        extractPromise.get("optionalString") mustEqual "defaultValue"
      }

      "Support optional parameters without a default " in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            OptionalStringCommandBeanExtractParameter("optionalString")
          )
        }

        val bean = new MapCommandBean(Map())

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beSuccessfulTry

        extractPromise.get.get("optionalString") mustEqual None
      }

      "Convert values to Strings if possible" in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            RequiredStringCommandBeanExtractParameter("stringInt"),
            RequiredStringCommandBeanExtractParameter("stringDouble"),
            RequiredStringCommandBeanExtractParameter("stringObject")
          )
        }

        val bean = new MapCommandBean(Map(
          "stringInt" -> new Integer(123),
          "stringDouble" -> new Double(123.456f),
          "stringObject" -> new Object {override def toString = "object"}
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beSuccessfulTry

        extractPromise.get("stringInt") mustEqual "123"
        extractPromise.get("stringDouble").asInstanceOf[String] must startWith("123.456")
        extractPromise.get("stringObject") mustEqual "object"
      }

      "Extract integer value" in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            RequiredIntCommandBeanExtractParameter("requiredInt")
          )
        }

        val bean = new MapCommandBean(Map(
          "requiredInt" -> new Integer(123)
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beSuccessfulTry

        extractPromise.get("requiredInt") mustEqual 123
      }

      "Convert numeric String value to integer" in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            RequiredIntCommandBeanExtractParameter("requiredInt")
          )
        }

        val bean = new MapCommandBean(Map(
          "requiredInt" -> "123"
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beSuccessfulTry

        extractPromise.get("requiredInt") mustEqual 123
      }

      "Extract DateTime " in {

        val formatter = DateTimeFormat.forPattern("yyyy/MM/dd/HH")

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            RequiredDateTimeCommandBeanExtractParameter("requiredDateTime", formatter)
          )
        }

        val bean = new MapCommandBean(Map(
          "requiredDateTime" -> "2015/01/01/01"
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beSuccessfulTry

        extractPromise.get("requiredDateTime").asInstanceOf[DateTime] mustEqual formatter.parseDateTime("2015/01/01/01")
      }

      "Fail cleanly if required String parameter is missing" in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            RequiredStringCommandBeanExtractParameter("requiredString")
          )
        }

        val bean = new MapCommandBean(Map(
          "notTheRequiredString" -> "value"
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beFailedTry
      }

      "Fail cleanly if String value can not be converted to required Int value" in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            RequiredIntCommandBeanExtractParameter("requiredInt")
          )
        }

        val bean = new MapCommandBean(Map(
          "requiredInt" -> "abc"
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beFailedTry
      }

      "Fail cleanly if exception from default value" in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            OptionalStringCommandBeanExtractParameter("optionalString", Some(() => {throw new Exception("failed")}))
          )
        }

        val bean = new MapCommandBean(Map())

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beFailedTry
      }

      "Run validation steps" in {

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            RequiredStringCommandBeanExtractParameter("requiredString")
          )

          override val CommandBeanExtractValidationSteps: Seq[ (Map[String, Any]) => Unit] = Seq (
            (extracted: Map[String, Any]) => {
              // success
            },
            (extracted: Map[String, Any]) => {
              throw new IllegalArgumentException("Validation exception")
            }
          )

        }

        val bean = new MapCommandBean(Map(
          "requiredString" -> "foo"
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beFailedTry
      }

      "Include all issues in final exception message" in {

        case class CustomCommandBeanExtractParameter(override val key: String) extends RequiredCommandBeanExtractParameter[String] {
          override def extractor(v: Any): String = {
            throw new Exception("Custom extraction failure")
          }
        }

        val testExtractor = new CommandBeanExtraction {
          override val CommandBeanExtractParameters = List[CommandBeanExtractParameter[_]](
            RequiredStringCommandBeanExtractParameter("requiredButMissingStringParameter"),
            OptionalStringCommandBeanExtractParameter("optionalStringParameter", Some(() => {throw new Exception()})),
            CustomCommandBeanExtractParameter("requiredCustomCommandParameter")
          )

          override val CommandBeanExtractValidationSteps: Seq[ (Map[String, Any]) => Unit] = Seq (
            (extracted: Map[String, Any]) => {
              throw new IllegalArgumentException("Validation exception 1")
            },
            (extracted: Map[String, Any]) => {
              throw new IllegalArgumentException("Validation exception 2")
            }
          )

        }

        val bean = new MapCommandBean(Map(
          "requiredCustomCommandParameter" -> "foo"
        ))

        val extractPromise = testExtractor.extractFromCommandBean(bean, passThroughFac)
        extractPromise must beFailedTry

        try {
          extractPromise.get
          failure
        } catch {
          case ex: Exception =>
            ex.getMessage() must contain("Missing required parameter 'requiredButMissingStringParameter'")
            ex.getMessage() must contain("Invalid value for 'optionalStringParameter'")
            ex.getMessage() must contain("Invalid value for 'requiredCustomCommandParameter'")
            ex.getMessage() must contain("Validation exception 1")
            ex.getMessage() must contain("Validation exception 2")
        }
        success
      }

    }
  }
}
