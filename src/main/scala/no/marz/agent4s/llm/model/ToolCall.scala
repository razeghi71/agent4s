package no.marz.agent4s.llm.model

sealed trait ToolCall:
  type I
  type O

  def id: String
  def tool: Tool[I, O]
  def input: I
