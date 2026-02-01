package no.marz.agent4s.llm.provider.claude

import io.circe.{Decoder, Encoder, Json, HCursor, DecodingFailure}
import io.circe.generic.semiauto.*
import io.circe.syntax.*

// ============================================================================
// Claude Messages API Request Models
// ============================================================================

/** Cache control for prompt caching
  * 
  * When set to "ephemeral", the content will be cached for 5 minutes (default)
  * or 1 hour if ttl is set to "1h".
  * 
  * Cached tokens don't count toward rate limits and cost 90% less!
  */
case class CacheControl(
    `type`: String = "ephemeral",
    ttl: Option[String] = None  // "5m" or "1h"
)

object CacheControl:
  /** Default 5-minute cache */
  val ephemeral: CacheControl = CacheControl("ephemeral")
  
  /** 1-hour cache (costs more to write but lasts longer) */
  val ephemeral1h: CacheControl = CacheControl("ephemeral", Some("1h"))

/** System content block with optional cache control */
case class ClaudeSystemBlock(
    `type`: String = "text",
    text: String,
    cache_control: Option[CacheControl] = None
)

/** Main request for Claude Messages API (POST /v1/messages)
  *
  * @param model The model to use (e.g., "claude-sonnet-4-5", "claude-opus-4")
  * @param max_tokens Maximum number of tokens to generate (required)
  * @param messages Array of messages in the conversation
  * @param system Optional system prompt (top-level, can be string or array of blocks for caching)
  * @param tools Optional list of tools available to the model
  * @param temperature Sampling temperature (0.0 to 1.0)
  * @param top_p Nucleus sampling parameter
  * @param stop_sequences Custom stop sequences
  */
case class ClaudeRequest(
    model: String,
    max_tokens: Int,
    messages: Seq[ClaudeMessage],
    system: Option[ClaudeSystemContent] = None,
    tools: Option[Seq[ClaudeTool]] = None,
    temperature: Option[Double] = None,
    top_p: Option[Double] = None,
    stop_sequences: Option[Seq[String]] = None
)

/** System content can be a simple string or array of blocks (for caching) */
sealed trait ClaudeSystemContent

object ClaudeSystemContent:
  case class Text(value: String) extends ClaudeSystemContent
  case class Blocks(blocks: Seq[ClaudeSystemBlock]) extends ClaudeSystemContent

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
  case class Text(
      text: String,
      cache_control: Option[CacheControl] = None
  ) extends ClaudeContentBlock:
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
      is_error: Option[Boolean] = None,
      cache_control: Option[CacheControl] = None
  ) extends ClaudeContentBlock:
    val `type` = "tool_result"

/** Tool definition for Claude API */
case class ClaudeTool(
    name: String,
    description: String,
    input_schema: Json,
    cache_control: Option[CacheControl] = None
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

/** Token usage information with cache stats */
case class ClaudeUsage(
    input_tokens: Int,
    output_tokens: Int,
    cache_creation_input_tokens: Option[Int] = None,
    cache_read_input_tokens: Option[Int] = None
):
  /** Total input tokens (including cached) */
  def totalInputTokens: Int = 
    input_tokens + cache_creation_input_tokens.getOrElse(0) + cache_read_input_tokens.getOrElse(0)
  
  /** Cache hit rate (0.0 to 1.0) */
  def cacheHitRate: Double =
    val total = totalInputTokens
    if total > 0 then cache_read_input_tokens.getOrElse(0).toDouble / total else 0.0

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
  
  // --- Cache Control ---
  given Encoder[CacheControl] = Encoder.instance { cc =>
    cc.ttl match
      case Some(ttl) => Json.obj("type" -> cc.`type`.asJson, "ttl" -> ttl.asJson)
      case None => Json.obj("type" -> cc.`type`.asJson)
  }
  given Decoder[CacheControl] = Decoder.instance { cursor =>
    for
      t <- cursor.get[String]("type")
      ttl <- cursor.get[Option[String]]("ttl")
    yield CacheControl(t, ttl)
  }
  
  // --- System Block ---
  given Encoder[ClaudeSystemBlock] = Encoder.instance { block =>
    val base = Json.obj("type" -> block.`type`.asJson, "text" -> block.text.asJson)
    block.cache_control match
      case Some(cc) => base.deepMerge(Json.obj("cache_control" -> cc.asJson))
      case None => base
  }
  given Decoder[ClaudeSystemBlock] = Decoder.instance { cursor =>
    for
      t <- cursor.get[String]("type")
      text <- cursor.get[String]("text")
      cc <- cursor.get[Option[CacheControl]]("cache_control")
    yield ClaudeSystemBlock(t, text, cc)
  }
  
  // --- System Content (string or blocks) ---
  given Encoder[ClaudeSystemContent] = Encoder.instance {
    case ClaudeSystemContent.Text(value) => value.asJson
    case ClaudeSystemContent.Blocks(blocks) => blocks.asJson
  }
  
  given Decoder[ClaudeSystemContent] = Decoder.instance { cursor =>
    cursor.as[String].map(ClaudeSystemContent.Text.apply).orElse(
      cursor.as[Seq[ClaudeSystemBlock]].map(ClaudeSystemContent.Blocks.apply)
    )
  }
  
  // --- Usage ---
  given Encoder[ClaudeUsage] = Encoder.instance { u =>
    val base = Json.obj(
      "input_tokens" -> u.input_tokens.asJson,
      "output_tokens" -> u.output_tokens.asJson
    )
    val withCacheCreation = u.cache_creation_input_tokens.fold(base)(c =>
      base.deepMerge(Json.obj("cache_creation_input_tokens" -> c.asJson))
    )
    u.cache_read_input_tokens.fold(withCacheCreation)(c =>
      withCacheCreation.deepMerge(Json.obj("cache_read_input_tokens" -> c.asJson))
    )
  }
  given Decoder[ClaudeUsage] = Decoder.instance { cursor =>
    for
      input <- cursor.get[Int]("input_tokens")
      output <- cursor.get[Int]("output_tokens")
      cacheCreate <- cursor.get[Option[Int]]("cache_creation_input_tokens")
      cacheRead <- cursor.get[Option[Int]]("cache_read_input_tokens")
    yield ClaudeUsage(input, output, cacheCreate, cacheRead)
  }
  
  // --- Error ---
  given Encoder[ClaudeError] = deriveEncoder[ClaudeError]
  given Decoder[ClaudeError] = deriveDecoder[ClaudeError]
  given Encoder[ClaudeErrorResponse] = deriveEncoder[ClaudeErrorResponse]
  given Decoder[ClaudeErrorResponse] = deriveDecoder[ClaudeErrorResponse]
  
  // --- Tool (with cache control) ---
  given Encoder[ClaudeTool] = Encoder.instance { tool =>
    val base = Json.obj(
      "name" -> tool.name.asJson,
      "description" -> tool.description.asJson,
      "input_schema" -> tool.input_schema
    )
    tool.cache_control match
      case Some(cc) => base.deepMerge(Json.obj("cache_control" -> cc.asJson))
      case None => base
  }
  given Decoder[ClaudeTool] = Decoder.instance { cursor =>
    for
      name <- cursor.get[String]("name")
      desc <- cursor.get[String]("description")
      schema <- cursor.get[Json]("input_schema")
      cc <- cursor.get[Option[CacheControl]]("cache_control")
    yield ClaudeTool(name, desc, schema, cc)
  }
  
  // --- Content Blocks (sealed trait) ---
  given Encoder[ClaudeContentBlock] = Encoder.instance {
    case ClaudeContentBlock.Text(text, cacheControl) =>
      val base = Json.obj("type" -> "text".asJson, "text" -> text.asJson)
      cacheControl match
        case Some(cc) => base.deepMerge(Json.obj("cache_control" -> cc.asJson))
        case None => base
    case ClaudeContentBlock.ToolUse(id, name, input) =>
      Json.obj(
        "type" -> "tool_use".asJson,
        "id" -> id.asJson,
        "name" -> name.asJson,
        "input" -> input
      )
    case ClaudeContentBlock.ToolResult(toolUseId, content, isError, cacheControl) =>
      val base = Json.obj(
        "type" -> "tool_result".asJson,
        "tool_use_id" -> toolUseId.asJson,
        "content" -> content.asJson
      )
      val withError = isError match
        case Some(err) => base.deepMerge(Json.obj("is_error" -> err.asJson))
        case None => base
      cacheControl match
        case Some(cc) => withError.deepMerge(Json.obj("cache_control" -> cc.asJson))
        case None => withError
  }
  
  given Decoder[ClaudeContentBlock] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap {
      case "text" =>
        for
          text <- cursor.get[String]("text")
          cc <- cursor.get[Option[CacheControl]]("cache_control")
        yield ClaudeContentBlock.Text(text, cc)
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
          cc <- cursor.get[Option[CacheControl]]("cache_control")
        yield ClaudeContentBlock.ToolResult(toolUseId, content, isError, cc)
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
