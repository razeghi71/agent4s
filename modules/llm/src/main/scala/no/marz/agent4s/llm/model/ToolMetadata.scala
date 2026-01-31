package no.marz.agent4s.llm.model
import io.circe.{Decoder, Encoder, Json}
import com.melvinlow.json.schema.JsonSchemaEncoder

trait ToolMetadata[I: {JsonSchemaEncoder, Decoder}, O: Encoder]:
  def name: String
  def description: String
  def inputSchema: Json = summon[JsonSchemaEncoder[I]].schema

  final def toToolSchema: ToolSchema =
    ToolSchema(name, description, inputSchema)
