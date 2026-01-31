package no.marz.agent4s.llm.model

import io.circe.Json

sealed trait AssistantContent

object AssistantContent:
  final case class Text(value: String) extends AssistantContent

  final case class ToolCalls(calls: Seq[ToolCall]) extends AssistantContent
