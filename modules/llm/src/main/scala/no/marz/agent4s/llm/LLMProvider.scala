package no.marz.agent4s.llm

import no.marz.agent4s.llm.model.{ChatCompletionRequest, ChatCompletionResponse}

trait LLMProvider[F[_]]:
  type Response <: ChatCompletionResponse
  def chatCompletion(request: ChatCompletionRequest): F[Response]
