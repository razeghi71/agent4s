package no.marz.agent4s.llm.provider.perplexity

case class PerplexityConfig(
    apiKey: String,
    baseUrl: String = "https://api.perplexity.ai"
)

object PerplexityConfig:
  def fromEnv: PerplexityConfig =
    PerplexityConfig(
      apiKey = sys.env.getOrElse(
        "PERPLEXITY_API_KEY",
        throw new RuntimeException(
          "PERPLEXITY_API_KEY environment variable not set"
        )
      )
    )
