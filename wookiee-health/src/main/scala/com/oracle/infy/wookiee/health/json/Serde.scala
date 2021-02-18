package com.oracle.infy.wookiee.health.json

import cats.effect.IO
import com.oracle.infy.wookiee.health.model._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

trait Serde {

  implicit def healthEncoder: Encoder[Health] = deriveEncoder[Health]
  implicit def healthDecoder: Decoder[Health] = deriveDecoder[Health]

  implicit def statusEncoder: Encoder[State] = Encoder.instance { state: State =>
    s"""${state.toStr}""".asJson
  }

  implicit def stateDecoder1: Decoder[State] =
    Decoder.instance(hc => {
      hc.as[String].map {
        case Normal.toStr   => Normal
        case Critical.toStr => Critical
        case Degraded.toStr => Degraded
      }
    })
  implicit def stateEntityDecoder: EntityDecoder[IO, State] = jsonOf[IO, State]
  implicit def stateEntityEncoder: EntityEncoder[IO, State] = jsonEncoderOf[IO, State]
  implicit def healthEntityEncoder: EntityEncoder[IO, Health] = jsonEncoderOf[IO, Health]
  implicit def healthEntityDecoder: EntityDecoder[IO, Health] = jsonOf[IO, Health]

}
