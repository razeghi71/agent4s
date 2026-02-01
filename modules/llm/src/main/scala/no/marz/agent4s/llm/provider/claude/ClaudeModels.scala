package no.marz.agent4s.llm.provider.claude

import io.circe.{Decoder, Encoder, Json, HCursor, DecodingFailure}
import io.circe.generic.semiauto.*
import io.circe.syntax.*

// ============================================================================
// Claude Messages API Request Models
// ============================================================================

/** Main request for Claude Messages API (POST /v1/messages)
  *
  * @param model The model to use (e.g., "claude-sonnet-4-5", "claude-opus-4")
  * @param max_tokens Maximum number of tokens to generate (required)
  * @param messages Array of messages in the conversation
  * @param system Optional system prompt (top-level, not in messages)
  * @param tools Optional list of tools available to the model
  * @param temperature Sampling temperature (0.0 to 1.0)
  * @param top_p Nucleus sampling parameter
  * @param stop_sequences Custom stop sequences
  */
case class ClaudeRequest(
    model: String,
    max_tokens: Int,
    messages: Seq[ClaudeMessage],
    system: Option[String] = None,
    tools: Option[Seq[ClaudeTool]] = None,
    temperature: Option[Double] = None,
    top_p: Option[Double] = None,
    stop_sequences: Option[Seq[String]] = None
)

/** Message in a Claude conversation */
case class ClaudeMessage(
    role: String,
    content: ClaudeContent
)

/** Content can be a simple string or array of content blocks */
sealed trait ClaudeContent

object ClaudeContent:
  case class Text(value: String) extends ClaudeContent
  case class Blocks(blocks: Seq[ClaudeContentBlock]) extends ClaudeContent

/** Content block types for Claude API */
sealed trait ClaudeContentBlock:
  def `type`: String

object ClaudeContentBlock:
  /** Text content block */
  case class Text(text: String) extends ClaudeContentBlock:
    val `type` = "text"

  /** Tool use block - Claude wants to call a tool */
  case class ToolUse(
      id: String,
      name: String,
      input: Json
  ) extends ClaudeContentBlock:
    val `type` = "tool_use"

  /** Tool result block - Result from executing a tool */
  case class ToolResult(
      tool_use_id: String,
      content: String,
      is_error: Option[Boolean] = None
  ) extends ClaudeContentBlock:
    val `type` = "tool_result"

/** Tool definition for Claude API */
case class ClaudeTool(
    name: String,
    description: String,
    input_schema: Json
)

// ============================================================================
// Claude Messages API Response Models
// ============================================================================

/** Response from Claude Messages API */
case class ClaudeResponse(
    id: String,
    `type`: String,
    role: String,
    content: Seq[ClaudeContentBlock],
    model: String,
    stop_reason: Option[String],
    stop_sequence: Option[String] = None,
    usage: ClaudeUsage
)

/** Token usage information */
case class ClaudeUsage(
    input_tokens: Int,
    output_tokens: Int
)

/** Error response from Claude API */
case class ClaudeError(
    `type`: String,
    message: String
)

case class ClaudeErrorResponse(
    `type`: String,
    error: ClaudeError
)

// ============================================================================
// Circe Codecs
// ============================================================================

object ClaudeModels:
  
  // --- Usage ---
  given Encoder[ClaudeUsage] = deriveEncoder[ClaudeUsage]
  given Decoder[ClaudeUsage] = deriveDecoder[ClaudeUsage]
  
  // --- Error ---
  given Encoder[ClaudeError] = deriveEncoder[ClaudeError]
  given Decoder[ClaudeError] = deriveDecoder[ClaudeError]
  given Encoder[ClaudeErrorResponse] = deriveEncoder[ClaudeErrorResponse]
  given Decoder[ClaudeErrorResponse] = deriveDecoder[ClaudeErrorResponse]
  
  // --- Tool ---
  given Encoder[ClaudeTool] = deriveEncoder[ClaudeTool]
  given Decoder[ClaudeTool] = deriveDecoder[ClaudeTool]
  
  // --- Content Blocks (sealed trait) ---
  given Encoder[ClaudeContentBlock] = Encoder.instance {
    case ClaudeContentBlock.Text(text) =>
      Json.obj("type" -> "text".asJson, "text" -> text.asJson)
    case ClaudeContentBlock.ToolUse(id, name, input) =>
      Json.obj(
        "type" -> "tool_use".asJson,
        "id" -> id.asJson,
        "name" -> name.asJson,
        "input" -> input
      )
    case ClaudeContentBlock.ToolResult(toolUseId, content, isError) =>
      val base = Json.obj(
        "type" -> "tool_result".asJson,
        "tool_use_id" -> toolUseId.asJson,
        "content" -> content.asJson
      )
      isError match
        case Some(err) => base.deepMerge(Json.obj("is_error" -> err.asJson))
        case None => base
  }
  
  given Decoder[ClaudeContentBlock] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap {
      case "text" =>
        cursor.get[String]("text").map(ClaudeContentBlock.Text.apply)
      case "tool_use" =>
        for
          id <- cursor.get[String]("id")
          name <- cursor.get[String]("name")
          input <- cursor.get[Json]("input")
        yield ClaudeContentBlock.ToolUse(id, name, input)
      case "tool_result" =>
        for
          toolUseId <- cursor.get[String]("tool_use_id")
          content <- cursor.get[String]("content")
          isError <- cursor.get[Option[Boolean]]("is_error")
        yield ClaudeContentBlock.ToolResult(toolUseId, content, isError)
      case other =>
        Left(DecodingFailure(s"Unknown content block type: $other", cursor.history))
    }
  }
  
  // --- Content (string or blocks) ---
  given Encoder[ClaudeContent] = Encoder.instance {
    case ClaudeContent.Text(value) => value.asJson
    case ClaudeContent.Blocks(blocks) => blocks.asJson
  }
  
  given Decoder[ClaudeContent] = Decoder.instance { cursor =>
    // Try string first, then array of blocks
    cursor.as[String].map(ClaudeContent.Text.apply).orElse(
      cursor.as[Seq[ClaudeContentBlock]].map(ClaudeContent.Blocks.apply)
    )
  }
  
  // --- Message ---
  given Encoder[ClaudeMessage] = Encoder.instance { msg =>
    Json.obj(
      "role" -> msg.role.asJson,
      "content" -> msg.content.asJson
    )
  }
  
  given Decoder[ClaudeMessage] = Decoder.instance { cursor =>
    for
      role <- cursor.get[String]("role")
      content <- cursor.get[ClaudeContent]("content")
    yield ClaudeMessage(role, content)
  }
  
  // --- Request ---
  given Encoder[ClaudeRequest] = Encoder.instance { req =>
    val base = Json.obj(
      "model" -> req.model.asJson,
      "max_tokens" -> req.max_tokens.asJson,
      "messages" -> req.messages.asJson
    )
    
    val withSystem = req.system.fold(base)(s => 
      base.deepMerge(Json.obj("system" -> s.asJson))
    )
    val withTools = req.tools.fold(withSystem)(t => 
      withSystem.deepMerge(Json.obj("tools" -> t.asJson))
    )
    val withTemp = req.temperature.fold(withTools)(t => 
      withTools.deepMerge(Json.obj("temperature" -> t.asJson))
    )
    val withTopP = req.top_p.fold(withTemp)(t => 
      withTemp.deepMerge(Json.obj("top_p" -> t.asJson))
    )
    val withStop = req.stop_sequences.fold(withTopP)(s => 
      withTopP.deepMerge(Json.obj("stop_sequences" -> s.asJson))
    )
    
    withStop
  }
  
  given Decoder[ClaudeRequest] = deriveDecoder[ClaudeRequest]
  
  // --- Response ---
  given Encoder[ClaudeResponse] = deriveEncoder[ClaudeResponse]
  given Decoder[ClaudeResponse] = deriveDecoder[ClaudeResponse]
