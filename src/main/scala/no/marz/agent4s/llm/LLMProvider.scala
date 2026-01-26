package no.marz.agent4s.llm

import no.marz.agent4s.llm.model.{ChatCompletionRequest, ChatCompletionResponse}

trait LLMProvider[F[_]]:
  def chatCompletion(request: ChatCompletionRequest): F[ChatCompletionResponse]
