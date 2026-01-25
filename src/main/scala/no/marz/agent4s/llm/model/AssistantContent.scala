package no.marz.agent4s.llm.model

sealed trait AssistantContent

object AssistantContent:
  final case class Text(value: String) extends AssistantContent

  final case class ToolCalls(
      calls: List[ToolCall]
  ) extends AssistantContent
