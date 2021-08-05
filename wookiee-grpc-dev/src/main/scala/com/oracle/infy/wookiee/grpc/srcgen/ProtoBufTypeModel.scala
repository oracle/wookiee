package com.oracle.infy.wookiee.grpc.srcgen

object ProtoBufTypeModel {

  sealed trait ScalaRecord

  sealed trait ScalaType

  sealed trait PrimitiveScalaType extends ScalaType

  final case class Param(name: String, `type`: ScalaType)

  final case class CaseClass(className: String, params: List[Param])

  // TODO
  // 1. Implement nested sealed traits
  // 2. Use types from "wrappers.proto" for Optional scalars (https://scalapb.github.io/docs/customizations#primitive-wrappers)
  // 3. Use named fields inside "oneof" (instead of a, b, c, etc)
  // 4. Ensure generated proto contains top level error
  /*
        message ASError {
          oneof OneOf {
            ValidationError validationError = 1;
            InvalidInputError invalidInputError = 2;
          }

          message ValidationError {}
          message InvalidInputError {}
        }
   */
  // 5. Add ability to import third-party proto file in generated proto file
  // 6. Add ability to define rpcs that use third-party message types
  // 7. Think about using annotations to exclude third-party types
  /*
      object Main {
        sealed trait ASError

        sealed trait DestinationError

        @provided(maxyError)
        case class ValidationError(code: Foo, maxyError: MaxymiserBaseError) extends ASError with DestinationError
      }
 */

}
