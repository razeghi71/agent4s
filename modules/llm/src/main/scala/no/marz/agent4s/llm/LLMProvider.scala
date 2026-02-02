package no.marz.agent4s.llm

import no.marz.agent4s.llm.model.{ChatCompletionRequest, ChatCompletionResponse}

/** Base trait for LLM providers.
  *
  * All providers (OpenAI, Claude, Perplexity, etc.) implement this trait.
  */
trait LLMProvider[F[_]]:
  /** Provider name for logging, metrics, and error reporting.
    * 
    * Examples: "openai", "claude", "perplexity", "openai-responses"
    */
  def name: String
  
  type Response <: ChatCompletionResponse
  
  def chatCompletion(request: ChatCompletionRequest): F[Response]
