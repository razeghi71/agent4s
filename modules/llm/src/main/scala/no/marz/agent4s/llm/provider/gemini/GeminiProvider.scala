package no.marz.agent4s.llm.provider.gemini

import cats.effect.kernel.Async
import cats.effect.Resource
import cats.syntax.all.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.circe.*
import org.typelevel.ci.CIString
import io.circe.syntax.*
import no.marz.agent4s.llm.LLMProvider
import no.marz.agent4s.llm.model.{Message as DomainMessage, *}
import no.marz.agent4s.llm.provider.openai.OpenAIChatResponse
import no.marz.agent4s.llm.provider.openai.OpenAIModels.given
import no.marz.agent4s.llm.provider.utils.OpenAICompatibleUtils

class GeminiProvider[F[_]: Async](
    client: Client[F],
    config: GeminiConfig
) extends LLMProvider[F]:

  val name: String = "gemini"

  type Response = ChatCompletionResponse

  def chatCompletion(request: ChatCompletionRequest)
      : F[ChatCompletionResponse] =
    for
      // 1. Convert domain request to OpenAI format
      openAIRequest <- OpenAICompatibleUtils.toOpenAIRequest(request)

      // 2. Build HTTP request
      httpRequest = OpenAICompatibleUtils.buildHttpRequest(
        openAIRequest,
        config.baseUrl,
        config.apiKey
      )

      // 3. Execute HTTP call with error handling
      openAIResponse <- client.run(httpRequest).use { response =>
        given EntityDecoder[F, OpenAIChatResponse] =
          jsonOf[F, OpenAIChatResponse]
        response.status.code match
          case code if response.status.isSuccess =>
            response.as[OpenAIChatResponse]
          case 429 =>
            val retryAfter = response.headers
              .get(CIString("retry-after"))
              .flatMap(_.head.value.toIntOption)
            response.as[String].flatMap { body =>
              Async[F].raiseError(RateLimitError(
                s"Gemini API rate limit exceeded: $body",
                retryAfter,
                Some(name)
              ))
            }
          case 401 | 403 =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(AuthenticationError(
                s"Gemini API authentication failed: $body",
                Some(name)
              ))
            }
          case 400 =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(InvalidRequestError(
                s"Gemini API invalid request: $body",
                Some(name)
              ))
            }
          case code if code >= 500 =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(ProviderUnavailableError(
                s"Gemini API server error: $body",
                Some(code),
                Some(name)
              ))
            }
          case code =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(new RuntimeException(
                s"Gemini API error ($code): $body"
              ))
            }
      }

      // 4. Convert to domain format using shared utility
      response <- OpenAICompatibleUtils.fromOpenAIResponse(openAIResponse)
    yield response

object GeminiProvider:
  def resource[F[_]: Async](
      config: GeminiConfig
  ): Resource[F, GeminiProvider[F]] =
    EmberClientBuilder.default[F].build.map { client =>
      new GeminiProvider[F](client, config)
    }

  def resourceFromEnv[F[_]: Async]: Resource[F, GeminiProvider[F]] =
    resource(GeminiConfig.fromEnv)

  def apply[F[_]: Async](
      client: Client[F],
      config: GeminiConfig
  ): GeminiProvider[F] =
    new GeminiProvider[F](client, config)
