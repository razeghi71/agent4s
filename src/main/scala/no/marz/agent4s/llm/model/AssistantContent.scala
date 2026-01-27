package no.marz.agent4s.llm.model

sealed trait AssistantContent

object AssistantContent:
  final case class Text(value: String) extends AssistantContent

  // Empty case class for now - will be populated later
  final case class ToolCalls() extends AssistantContent
