package no.marz.agent4s.llm.provider.deepseek

case class DeepSeekConfig(
    apiKey: String,
    baseUrl: String = "https://api.deepseek.com"
)

object DeepSeekConfig:
  def fromEnv: DeepSeekConfig =
    DeepSeekConfig(
      apiKey = sys.env.getOrElse(
        "DEEPSEEK_API_KEY",
        throw new RuntimeException(
          "DEEPSEEK_API_KEY environment variable not set"
        )
      )
    )
