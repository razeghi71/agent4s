package no.marz.agent4s.llm.model

sealed trait ToolCall[F[_]]:
  type I
  type O

  def id: String
  def tool: Tool[F, I, O]
  def input: I
