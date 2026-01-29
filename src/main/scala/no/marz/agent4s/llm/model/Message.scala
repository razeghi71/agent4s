package no.marz.agent4s.llm.model
import no.marz.agent4s.llm.model.{AssistantContent, Role}

sealed trait Message:
  def role: Role

object Message:
  final case class System(content: String) extends Message:
    val role: Role = Role.System

  final case class User(content: String) extends Message:
    val role: Role = Role.User

  final case class Assistant(content: AssistantContent) extends Message:
    val role: Role = Role.Assistant

  final case class Tool(
      toolCallId: String,
      toolName: String,
      content: String
  ) extends Message:
    val role: Role = Role.Tool
