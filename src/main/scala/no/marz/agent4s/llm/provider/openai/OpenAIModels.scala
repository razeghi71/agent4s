package no.marz.agent4s.llm.provider.openai

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._
import io.circe.syntax._

// Request models
case class OpenAIChatRequest(
  model: String,
  messages: Seq[OpenAIMessage],
  tools: Option[Seq[OpenAITool]] = None,
  temperature: Option[Double] = None,
  max_tokens: Option[Int] = None,
  top_p: Option[Double] = None
)

case class OpenAITool(
  `type`: String,
  function: OpenAIFunction
)

case class OpenAIFunction(
  name: String,
  description: String,
  parameters: Json
)

case class OpenAIMessage(
  role: String,
  content: Option[String] = None,
  tool_calls: Option[Seq[OpenAIToolCall]] = None,
  tool_call_id: Option[String] = None,
  name: Option[String] = None
)

case class OpenAIToolCall(
  id: String,
  `type`: String,
  function: OpenAIFunctionCall
)

case class OpenAIFunctionCall(
  name: String,
  arguments: String
)

// Response models
case class OpenAIChatResponse(
  id: String,
  `object`: String,
  created: Long,
  model: String,
  choices: Seq[OpenAIChoice],
  usage: OpenAIUsage
)

case class OpenAIChoice(
  index: Int,
  message: OpenAIMessage,
  finish_reason: Option[String]
)

case class OpenAIUsage(
  prompt_tokens: Int,
  completion_tokens: Int,
  total_tokens: Int
)

// Circe codecs
object OpenAIModels:
  given Encoder[OpenAIFunction] = deriveEncoder[OpenAIFunction]
  given Decoder[OpenAIFunction] = deriveDecoder[OpenAIFunction]
  
  given Encoder[OpenAITool] = deriveEncoder[OpenAITool]
  given Decoder[OpenAITool] = deriveDecoder[OpenAITool]
  
  given Encoder[OpenAIFunctionCall] = deriveEncoder[OpenAIFunctionCall]
  given Decoder[OpenAIFunctionCall] = deriveDecoder[OpenAIFunctionCall]
  
  given Encoder[OpenAIToolCall] = deriveEncoder[OpenAIToolCall]
  given Decoder[OpenAIToolCall] = deriveDecoder[OpenAIToolCall]
  
  given Encoder[OpenAIMessage] = deriveEncoder[OpenAIMessage]
  given Decoder[OpenAIMessage] = deriveDecoder[OpenAIMessage]
  
  given Encoder[OpenAIChatRequest] = deriveEncoder[OpenAIChatRequest]
  given Decoder[OpenAIChatRequest] = deriveDecoder[OpenAIChatRequest]
  
  given Encoder[OpenAIUsage] = deriveEncoder[OpenAIUsage]
  given Decoder[OpenAIUsage] = deriveDecoder[OpenAIUsage]
  
  given Encoder[OpenAIChoice] = deriveEncoder[OpenAIChoice]
  given Decoder[OpenAIChoice] = deriveDecoder[OpenAIChoice]
  
  given Encoder[OpenAIChatResponse] = deriveEncoder[OpenAIChatResponse]
  given Decoder[OpenAIChatResponse] = deriveDecoder[OpenAIChatResponse]
