package no.marz.agent4s.llm.provider.claude

/** Configuration for Claude API provider.
  *
  * @param apiKey The Anthropic API key
  * @param baseUrl The base URL for the Claude API (default: https://api.anthropic.com/v1)
  * @param anthropicVersion The API version header (default: 2023-06-01)
  * @param defaultMaxTokens Default max tokens if not specified in request (Claude requires this)
  */
case class ClaudeConfig(
    apiKey: String,
    baseUrl: String = "https://api.anthropic.com/v1",
    anthropicVersion: String = "2023-06-01",
    defaultMaxTokens: Int = 4096
)

object ClaudeConfig:
  /** Create config from environment variables.
    * 
    * Required: ANTHROPIC_API_KEY
    * Optional: ANTHROPIC_BASE_URL, ANTHROPIC_VERSION
    */
  def fromEnv: ClaudeConfig =
    ClaudeConfig(
      apiKey = sys.env.getOrElse(
        "ANTHROPIC_API_KEY",
        throw new RuntimeException(
          "ANTHROPIC_API_KEY environment variable not set"
        )
      ),
      baseUrl = sys.env.getOrElse("ANTHROPIC_BASE_URL", "https://api.anthropic.com/v1"),
      anthropicVersion = sys.env.getOrElse("ANTHROPIC_VERSION", "2023-06-01")
    )
