package no.marz.agent4s.llm.provider.openai

import io.circe.{Decoder, Encoder, Json, HCursor, DecodingFailure}
import io.circe.generic.semiauto.*
import io.circe.syntax.*

// ============================================================================
// Responses API Request Models
// ============================================================================

/** Main request for OpenAI Responses API (POST /v1/responses)
  *
  * @param model
  *   The model to use (e.g., "gpt-5.2", "o3", "o4-mini")
  * @param input
  *   The user input - can be a string or array of input items
  * @param instructions
  *   System instructions for the model (replaces system message)
  * @param tools
  *   List of tools available to the model
  * @param previousResponseId
  *   ID of a previous response for multi-turn conversations
  * @param temperature
  *   Sampling temperature (0.0 to 2.0)
  * @param maxOutputTokens
  *   Maximum tokens to generate
  * @param topP
  *   Nucleus sampling parameter
  * @param store
  *   Whether to store the response for later retrieval
  * @param metadata
  *   Custom metadata for the response
  */
case class ResponsesRequest(
    model: String,
    input: ResponsesInput,
    instructions: Option[String] = None,
    tools: Option[Seq[ResponsesTool]] = None,
    previous_response_id: Option[String] = None,
    temperature: Option[Double] = None,
    max_output_tokens: Option[Int] = None,
    top_p: Option[Double] = None,
    store: Option[Boolean] = None,
    metadata: Option[Map[String, String]] = None
)

/** Input for Responses API - can be simple string or structured items */
sealed trait ResponsesInput

object ResponsesInput:
  /** Simple text input */
  case class Text(value: String) extends ResponsesInput

  /** Array of input items for complex conversations */
  case class Items(items: Seq[ResponsesInputItem]) extends ResponsesInput

/** Input item types for the Responses API */
sealed trait ResponsesInputItem:
  def `type`: String

object ResponsesInputItem:
  /** User message input */
  case class Message(
      role: String,
      content: String
  ) extends ResponsesInputItem:
    val `type` = "message"

  /** Function call from assistant (needed when providing function_call_output)
    */
  case class FunctionCall(
      call_id: String,
      name: String,
      arguments: String
  ) extends ResponsesInputItem:
    val `type` = "function_call"

  /** Result from a function call */
  case class FunctionCallOutput(
      call_id: String,
      output: String
  ) extends ResponsesInputItem:
    val `type` = "function_call_output"

  /** Reference to a previous item (for context) */
  case class ItemReference(
      id: String
  ) extends ResponsesInputItem:
    val `type` = "item_reference"

/** Tool definition for Responses API (flattened structure) */
case class ResponsesTool(
    `type`: String,
    name: String,
    description: String,
    parameters: Json,
    strict: Option[Boolean] = None
)

// ============================================================================
// Responses API Response Models
// ============================================================================

/** Main response from OpenAI Responses API */
case class ResponsesResponse(
    id: String,
    `object`: String,
    created_at: Long,
    model: String,
    output: Seq[ResponsesOutputItem],
    usage: Option[ResponsesUsage] = None,
    status: String,
    error: Option[ResponsesError] = None
)

/** Output item types from Responses API */
sealed trait ResponsesOutputItem:
  def `type`: String
  def id: Option[String]

object ResponsesOutputItem:
  /** Text message output */
  case class Message(
      id: Option[String],
      role: String,
      content: Seq[MessageContent]
  ) extends ResponsesOutputItem:
    val `type` = "message"

  /** Function call output - the model wants to call a tool */
  case class FunctionCall(
      id: Option[String],
      call_id: String,
      name: String,
      arguments: String
  ) extends ResponsesOutputItem:
    val `type` = "function_call"

  /** Reasoning output (for o-series models) */
  case class Reasoning(
      id: Option[String],
      summary: Seq[ReasoningSummary]
  ) extends ResponsesOutputItem:
    val `type` = "reasoning"

/** Content within a message */
sealed trait MessageContent:
  def `type`: String

object MessageContent:
  case class OutputText(text: String) extends MessageContent:
    val `type` = "output_text"

  case class Refusal(refusal: String) extends MessageContent:
    val `type` = "refusal"

/** Reasoning summary for o-series models */
case class ReasoningSummary(
    `type`: String,
    text: String
)

/** Usage information for Responses API */
case class ResponsesUsage(
    input_tokens: Int,
    output_tokens: Int,
    total_tokens: Int,
    input_tokens_details: Option[TokenDetails] = None,
    output_tokens_details: Option[TokenDetails] = None
)

case class TokenDetails(
    cached_tokens: Option[Int] = None,
    reasoning_tokens: Option[Int] = None
)

/** Error information */
case class ResponsesError(
    code: String,
    message: String
)

// ============================================================================
// Circe Codecs
// ============================================================================

object ResponsesModels:

  // --- Token Details ---
  given Encoder[TokenDetails] = deriveEncoder[TokenDetails]
  given Decoder[TokenDetails] = deriveDecoder[TokenDetails]

  // --- Usage ---
  given Encoder[ResponsesUsage] = deriveEncoder[ResponsesUsage]
  given Decoder[ResponsesUsage] = deriveDecoder[ResponsesUsage]

  // --- Error ---
  given Encoder[ResponsesError] = deriveEncoder[ResponsesError]
  given Decoder[ResponsesError] = deriveDecoder[ResponsesError]

  // --- Reasoning Summary ---
  given Encoder[ReasoningSummary] = deriveEncoder[ReasoningSummary]
  given Decoder[ReasoningSummary] = deriveDecoder[ReasoningSummary]

  // --- Message Content (sealed trait) ---
  given Encoder[MessageContent] = Encoder.instance {
    case MessageContent.OutputText(text) =>
      Json.obj("type" -> "output_text".asJson, "text" -> text.asJson)
    case MessageContent.Refusal(refusal) =>
      Json.obj("type" -> "refusal".asJson, "refusal" -> refusal.asJson)
  }

  given Decoder[MessageContent] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap {
      case "output_text" =>
        cursor.get[String]("text").map(MessageContent.OutputText.apply)
      case "refusal" =>
        cursor.get[String]("refusal").map(MessageContent.Refusal.apply)
      case other => Left(DecodingFailure(
          s"Unknown message content type: $other",
          cursor.history
        ))
    }
  }

  // --- Output Items (sealed trait) ---
  given Encoder[ResponsesOutputItem] = Encoder.instance {
    case ResponsesOutputItem.Message(id, role, content) =>
      Json.obj(
        "type" -> "message".asJson,
        "id" -> id.asJson,
        "role" -> role.asJson,
        "content" -> content.asJson
      )
    case ResponsesOutputItem.FunctionCall(id, callId, name, arguments) =>
      Json.obj(
        "type" -> "function_call".asJson,
        "id" -> id.asJson,
        "call_id" -> callId.asJson,
        "name" -> name.asJson,
        "arguments" -> arguments.asJson
      )
    case ResponsesOutputItem.Reasoning(id, summary) =>
      Json.obj(
        "type" -> "reasoning".asJson,
        "id" -> id.asJson,
        "summary" -> summary.asJson
      )
  }

  given Decoder[ResponsesOutputItem] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap {
      case "message" =>
        for
          id <- cursor.get[Option[String]]("id")
          role <- cursor.get[String]("role")
          content <- cursor.get[Seq[MessageContent]]("content")
        yield ResponsesOutputItem.Message(id, role, content)
      case "function_call" =>
        for
          id <- cursor.get[Option[String]]("id")
          callId <- cursor.get[String]("call_id")
          name <- cursor.get[String]("name")
          arguments <- cursor.get[String]("arguments")
        yield ResponsesOutputItem.FunctionCall(id, callId, name, arguments)
      case "reasoning" =>
        for
          id <- cursor.get[Option[String]]("id")
          summary <- cursor.get[Seq[ReasoningSummary]]("summary")
        yield ResponsesOutputItem.Reasoning(id, summary)
      case other =>
        Left(DecodingFailure(
          s"Unknown output item type: $other",
          cursor.history
        ))
    }
  }

  // --- Input Items (sealed trait) ---
  given Encoder[ResponsesInputItem] = Encoder.instance {
    case ResponsesInputItem.Message(role, content) =>
      Json.obj(
        "type" -> "message".asJson,
        "role" -> role.asJson,
        "content" -> content.asJson
      )
    case ResponsesInputItem.FunctionCall(callId, name, arguments) =>
      Json.obj(
        "type" -> "function_call".asJson,
        "call_id" -> callId.asJson,
        "name" -> name.asJson,
        "arguments" -> arguments.asJson
      )
    case ResponsesInputItem.FunctionCallOutput(callId, output) =>
      Json.obj(
        "type" -> "function_call_output".asJson,
        "call_id" -> callId.asJson,
        "output" -> output.asJson
      )
    case ResponsesInputItem.ItemReference(id) =>
      Json.obj(
        "type" -> "item_reference".asJson,
        "id" -> id.asJson
      )
  }

  given Decoder[ResponsesInputItem] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap {
      case "message" =>
        for
          role <- cursor.get[String]("role")
          content <- cursor.get[String]("content")
        yield ResponsesInputItem.Message(role, content)
      case "function_call" =>
        for
          callId <- cursor.get[String]("call_id")
          name <- cursor.get[String]("name")
          arguments <- cursor.get[String]("arguments")
        yield ResponsesInputItem.FunctionCall(callId, name, arguments)
      case "function_call_output" =>
        for
          callId <- cursor.get[String]("call_id")
          output <- cursor.get[String]("output")
        yield ResponsesInputItem.FunctionCallOutput(callId, output)
      case "item_reference" =>
        cursor.get[String]("id").map(ResponsesInputItem.ItemReference.apply)
      case other =>
        Left(DecodingFailure(
          s"Unknown input item type: $other",
          cursor.history
        ))
    }
  }

  // --- Responses Input (sealed trait) ---
  given Encoder[ResponsesInput] = Encoder.instance {
    case ResponsesInput.Text(value)  => value.asJson
    case ResponsesInput.Items(items) => items.asJson
  }

  given Decoder[ResponsesInput] = Decoder.instance { cursor =>
    // Try to decode as string first, then as array
    cursor.as[String].map(ResponsesInput.Text.apply).orElse(
      cursor.as[Seq[ResponsesInputItem]].map(ResponsesInput.Items.apply)
    )
  }

  // --- Tool ---
  given Encoder[ResponsesTool] = deriveEncoder[ResponsesTool]
  given Decoder[ResponsesTool] = deriveDecoder[ResponsesTool]

  // --- Request ---
  given Encoder[ResponsesRequest] = deriveEncoder[ResponsesRequest]
  given Decoder[ResponsesRequest] = deriveDecoder[ResponsesRequest]

  // --- Response ---
  given Encoder[ResponsesResponse] = deriveEncoder[ResponsesResponse]
  given Decoder[ResponsesResponse] = deriveDecoder[ResponsesResponse]
