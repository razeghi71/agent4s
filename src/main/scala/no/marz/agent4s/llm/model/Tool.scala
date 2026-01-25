package no.marz.agent4s.llm.model
import io.circe.{Decoder, Encoder, Json}
import com.melvinlow.json.schema.JsonSchemaEncoder
export com.melvinlow.json.schema.generic.auto.given
export io.circe.generic.auto.given

trait ToolCodec[A]:
  def schema: Json
  def decoder: Decoder[A]
  def encoder: Encoder[A]

object ToolCodec:
  def make[A](
      enc: Encoder[A],
      dec: Decoder[A],
      json: JsonSchemaEncoder[A]
  ): ToolCodec[A] =
    new ToolCodec[A]:
      def schema: Json = json.schema
      def decoder: Decoder[A] = dec
      def encoder: Encoder[A] = enc

  inline given derive[A](using
      enc: Encoder[A],
      dec: Decoder[A],
      json: JsonSchemaEncoder[A]
  ): ToolCodec[A] = make(enc, dec, json)

trait Tool[I: ToolCodec, O: ToolCodec]:
  def name: String
  def description: String
  def execute(input: I): O
  def schema: Json = 
    Json.obj(
      "name" -> Json.fromString(name),
      "description" -> Json.fromString(description),
      "input_schema" -> summon[ToolCodec[I]].schema
    )
