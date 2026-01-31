package no.marz.agent4s.llm.model

import io.circe.Json

case class ToolCall(
    id: String,
    name: String,
    arguments: Json
)
