package no.marz.agent4s.llm.provider.openai

import cats.effect.kernel.Async
import cats.effect.Resource
import cats.syntax.all.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.circe.*
import org.http4s.headers.*
import org.typelevel.ci.CIString
import io.circe.syntax.*
import no.marz.agent4s.llm.LLMProvider
import no.marz.agent4s.llm.model.{Message as DomainMessage, *}
import no.marz.agent4s.llm.provider.openai.OpenAIModels.given
import no.marz.agent4s.llm.provider.utils.OpenAICompatibleUtils

/** OpenAI Completion Provider using the Chat Completions API.
  * 
  * This provider supports models like GPT-4o, GPT-4, GPT-3.5-turbo.
  * For GPT-5.2 and newer models, use OpenAIResponsesProvider instead.
  */
class OpenAICompletionProvider[F[_]: Async](
    client: Client[F],
    config: OpenAIConfig
) extends LLMProvider[F]:

  type Response = ChatCompletionResponse

  def chatCompletion(request: ChatCompletionRequest)
      : F[ChatCompletionResponse] =
    for
      // 1. Convert domain request to OpenAI format
      openAIRequest <- OpenAICompatibleUtils.toOpenAIRequest(request)

      // 2. Build HTTP request
      httpRequest = buildHttpRequest(openAIRequest)

      // 3. Execute HTTP call and decode response
      openAIResponse <- client.expect[OpenAIChatResponse](httpRequest)(using
        jsonOf[F, OpenAIChatResponse]
      )

      // 4. Convert OpenAI response back to domain format
      response <- OpenAICompatibleUtils.fromOpenAIResponse(openAIResponse)
    yield response

  private def buildHttpRequest(req: OpenAIChatRequest): Request[F] =
    val orgHeaders = config.organization
      .map(org => Header.Raw(CIString("OpenAI-Organization"), org))
      .map(h => Headers(h))
      .getOrElse(Headers.empty)

    OpenAICompatibleUtils.buildHttpRequest(
      req,
      config.baseUrl,
      config.apiKey,
      orgHeaders
    )

object OpenAICompletionProvider:
  def resource[F[_]: Async](config: OpenAIConfig): Resource[F, LLMProvider[F]] =
    EmberClientBuilder.default[F].build.map { client =>
      new OpenAICompletionProvider[F](client, config)
    }

  def resourceFromEnv[F[_]: Async]: Resource[F, LLMProvider[F]] =
    resource(OpenAIConfig.fromEnv)

// Backward compatibility alias
@deprecated("Use OpenAICompletionProvider instead", "0.2.0")
type OpenAIProvider[F[_]] = OpenAICompletionProvider[F]

@deprecated("Use OpenAICompletionProvider instead", "0.2.0")
val OpenAIProvider = OpenAICompletionProvider
