package com.oracle.infy.wookiee.grpc.json
import com.oracle.infy.wookiee.model.{Host, HostMetadata}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

object HostSerde {

  final case class HostSerdeError(msg: String)

  implicit val hostMetadataDecoder: Decoder[HostMetadata] = deriveDecoder[HostMetadata]
  implicit val hostMetadataEncoder: Encoder[HostMetadata] = deriveEncoder[HostMetadata]

  implicit val hostDecoder: Decoder[Host] = deriveDecoder[Host]
  implicit val hostEncoder: Encoder[Host] = deriveEncoder[Host]

  private val encoding = "utf8"

  def deserialize(data: Array[Byte]): Either[HostSerdeError, Host] = {
    parse(new String(data, encoding)) match {
      case Left(err) => Left(HostSerdeError(s"Invalid json: ${err.message}"))
      case Right(json) =>
        json.as[Host] match {
          case Left(err)   => Left(HostSerdeError(s"Unable to parse json into host object: ${err.message}"))
          case Right(host) => Right(host)
        }
    }
  }

  def serialize(data: Host): Array[Byte] = data.asJson.noSpaces.getBytes(encoding)

}
