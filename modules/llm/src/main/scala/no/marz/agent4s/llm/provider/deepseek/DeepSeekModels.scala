package no.marz.agent4s.llm.provider.deepseek

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto.*
import no.marz.agent4s.llm.provider.openai.{OpenAIMessage, OpenAIUsage}
import no.marz.agent4s.llm.provider.openai.OpenAIModels.given

// DeepSeek extends OpenAI format with reasoning_content and cache token stats
case class DeepSeekChatResponse(
    id: String,
    `object`: String,
    created: Long,
    model: String,
    choices: Seq[DeepSeekChoice],
    usage: DeepSeekUsage
)

case class DeepSeekChoice(
    index: Int,
    message: DeepSeekMessage,
    finish_reason: Option[String]
)

// Extends OpenAIMessage with optional reasoning_content for deepseek-reasoner
case class DeepSeekMessage(
    role: String,
    content: Option[String] = None,
    reasoning_content: Option[String] = None,
    tool_calls: Option[Seq[no.marz.agent4s.llm.provider.openai.OpenAIToolCall]] = None,
    tool_call_id: Option[String] = None,
    name: Option[String] = None
)

case class DeepSeekUsage(
    prompt_tokens: Int,
    completion_tokens: Int,
    total_tokens: Int,
    prompt_cache_hit_tokens: Option[Int] = None,
    prompt_cache_miss_tokens: Option[Int] = None,
    completion_tokens_details: Option[DeepSeekCompletionTokensDetails] = None
)

case class DeepSeekCompletionTokensDetails(
    reasoning_tokens: Option[Int] = None
)

// Circe codecs
object DeepSeekModels:
  import no.marz.agent4s.llm.provider.openai.OpenAIModels.given

  given Encoder[DeepSeekCompletionTokensDetails] = deriveEncoder[DeepSeekCompletionTokensDetails]
  given Decoder[DeepSeekCompletionTokensDetails] = deriveDecoder[DeepSeekCompletionTokensDetails]

  given Encoder[DeepSeekUsage] = deriveEncoder[DeepSeekUsage]
  given Decoder[DeepSeekUsage] = deriveDecoder[DeepSeekUsage]

  given Encoder[DeepSeekMessage] = deriveEncoder[DeepSeekMessage]
  given Decoder[DeepSeekMessage] = deriveDecoder[DeepSeekMessage]

  given Encoder[DeepSeekChoice] = deriveEncoder[DeepSeekChoice]
  given Decoder[DeepSeekChoice] = deriveDecoder[DeepSeekChoice]

  given Encoder[DeepSeekChatResponse] = deriveEncoder[DeepSeekChatResponse]
  given Decoder[DeepSeekChatResponse] = deriveDecoder[DeepSeekChatResponse]
