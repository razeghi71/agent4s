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

trait Tool[F[_], I: ToolCodec, O: ToolCodec]:
  def name: String
  def description: String
  def execute(input: I): F[O]
  def schema: Json = summon[ToolCodec[I]].schema

// HList for type-safe heterogeneous tool lists
sealed trait ToolList[F[_]]

case class ToolNil[F[_]]() extends ToolList[F]

case class ToolCons[F[_], I, O, T <: ToolList[F]](
    head: Tool[F, I, O],
    tail: T
) extends ToolList[F]

extension [F[_], I: ToolCodec, O: ToolCodec](tool: Tool[F, I, O])
  def ~:[T <: ToolList[F]](tail: T): ToolCons[F, I, O, T] =
    ToolCons(tool, tail)
