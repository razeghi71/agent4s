package no.marz.agent4s.llm.model

/** Base trait for LLM provider errors */
sealed trait LLMError extends RuntimeException:
  def message: String
  override def getMessage: String = message

/** Rate limit error with retry information.
  *
  * Thrown when an LLM provider returns 429 (Too Many Requests).
  * Contains optional retry-after hint from the API response headers.
  *
  * This is a common error across all providers (OpenAI, Anthropic, Perplexity, etc.)
  *
  * Example handling:
  * {{{
  * provider.chatCompletion(request).handleErrorWith {
  *   case RateLimitError(msg, Some(seconds)) =>
  *     IO.sleep(seconds.seconds) >> provider.chatCompletion(request)
  *   case RateLimitError(msg, None) =>
  *     IO.sleep(1.second) >> provider.chatCompletion(request)
  * }
  * }}}
  *
  * @param message Error message from the provider
  * @param retryAfterSeconds Optional hint from provider on when to retry (in seconds)
  * @param provider Name of the provider that returned the error (e.g., "claude", "openai")
  */
case class RateLimitError(
    message: String,
    retryAfterSeconds: Option[Int] = None,
    provider: Option[String] = None
) extends LLMError

/** Authentication error - invalid or missing API key.
  *
  * Thrown when provider returns 401 or 403.
  */
case class AuthenticationError(
    message: String,
    provider: Option[String] = None
) extends LLMError

/** Invalid request error - malformed request or invalid parameters.
  *
  * Thrown when provider returns 400.
  */
case class InvalidRequestError(
    message: String,
    provider: Option[String] = None
) extends LLMError

/** Provider unavailable - server error or service down.
  *
  * Thrown when provider returns 5xx errors.
  */
case class ProviderUnavailableError(
    message: String,
    statusCode: Option[Int] = None,
    provider: Option[String] = None
) extends LLMError
