package com.oracle.infy.wookiee.grpc.json
import com.oracle.infy.wookiee.model.Host
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

object HostSerde {

  final case class HostSerdeError(msg: String)

  implicit private val hostDecoder: Decoder[Host] = deriveDecoder[Host]
  implicit private val hostEncoder: Encoder[Host] = deriveEncoder[Host]

  def deserialize(data: Array[Byte]): Either[HostSerdeError, Host] = {
    parse(new String(data, "utf8")) match {
      case Left(err) => Left(HostSerdeError(s"Invalid json: ${err.message}"))
      case Right(json) =>
        json.as[Host] match {
          case Left(err)   => Left(HostSerdeError(s"Unable to parse json into host object: ${err.message}"))
          case Right(host) => Right(host)
        }
    }
  }

  def serialize(data: Host): Array[Byte] = data.asJson.noSpaces.getBytes("utf8")

}
