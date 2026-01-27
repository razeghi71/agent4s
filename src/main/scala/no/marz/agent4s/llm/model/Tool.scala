package no.marz.agent4s.llm.model
import io.circe.{Decoder, Encoder, Json}
import com.melvinlow.json.schema.JsonSchemaEncoder

trait ToolCodec[A]:
  def schema: Json
  def decoder: Decoder[A]
  def encoder: Encoder[A]

object ToolCodec:
  private def make[A](
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
  def schema: Json = summon[ToolCodec[I]].schema

// HList for type-safe heterogeneous tool lists
sealed trait ToolList

case object ToolNil extends ToolList

case class ToolCons[I, O, T <: ToolList](
    head: Tool[I, O],
    tail: T
) extends ToolList

extension [I: ToolCodec, O: ToolCodec](tool: Tool[I, O])
  def ~:[T <: ToolList](tail: T): ToolCons[I, O, T] =
    ToolCons(tool, tail)
