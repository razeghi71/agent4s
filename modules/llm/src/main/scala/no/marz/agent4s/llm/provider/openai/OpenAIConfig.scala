package no.marz.agent4s.llm.provider.openai

case class OpenAIConfig(
    apiKey: String,
    baseUrl: String = "https://api.openai.com/v1",
    organization: Option[String] = None
)

object OpenAIConfig:
  def fromEnv: OpenAIConfig =
    OpenAIConfig(
      apiKey = sys.env.getOrElse(
        "OPENAI_API_KEY",
        throw new RuntimeException(
          "OPENAI_API_KEY environment variable not set"
        )
      ),
      organization = sys.env.get("OPENAI_ORG_ID")
    )
