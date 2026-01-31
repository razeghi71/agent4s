package no.marz.agent4s.llm.model
import io.circe.{Decoder, Encoder}
import com.melvinlow.json.schema.JsonSchemaEncoder

// Tool adds execution capability to ToolMetadata
trait Tool[F[_], I: {JsonSchemaEncoder, Decoder}, O: Encoder]
    extends ToolMetadata[I, O]:
  def execute(input: I): F[O]
