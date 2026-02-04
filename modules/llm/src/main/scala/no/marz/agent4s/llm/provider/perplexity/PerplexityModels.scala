package no.marz.agent4s.llm.provider.perplexity

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import no.marz.agent4s.llm.provider.openai.{OpenAIMessage, OpenAIUsage}
import no.marz.agent4s.llm.provider.openai.OpenAIModels.given

// Perplexity response extends OpenAI format with citations
case class PerplexityChatResponse(
    id: String,
    `object`: String,
    created: Long,
    model: String,
    choices: Seq[PerplexityChoice],
    usage: OpenAIUsage,
    citations: Option[Seq[String]] = None
)

case class PerplexityChoice(
    index: Int,
    message: OpenAIMessage,
    finish_reason: Option[String]
)

// Circe codecs
object PerplexityModels:
  // OpenAI types codecs are imported above via OpenAIModels.given

  given Encoder[PerplexityChoice] = deriveEncoder[PerplexityChoice]
  given Decoder[PerplexityChoice] = deriveDecoder[PerplexityChoice]

  given Encoder[PerplexityChatResponse] = deriveEncoder[PerplexityChatResponse]
  given Decoder[PerplexityChatResponse] = deriveDecoder[PerplexityChatResponse]
