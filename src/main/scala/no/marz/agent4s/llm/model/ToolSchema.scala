package no.marz.agent4s.llm.model
import io.circe.Json

// Simple case class for tool schema (for API requests)
case class ToolSchema(
    name: String,
    description: String,
    parameters: Json
)
