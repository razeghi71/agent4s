package no.marz.agent4s.llm.provider.gemini

case class GeminiConfig(
    apiKey: String,
    baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai"
)

object GeminiConfig:
  def fromEnv: GeminiConfig =
    GeminiConfig(
      apiKey = sys.env.getOrElse(
        "GOOGLE_API_KEY",
        throw new RuntimeException(
          "GOOGLE_API_KEY environment variable not set"
        )
      )
    )
