package no.marz.agent4s.llm.provider.deepseek

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
import no.marz.agent4s.llm.provider.openai.OpenAIModels.given
import no.marz.agent4s.llm.provider.deepseek.DeepSeekModels.given
import no.marz.agent4s.llm.provider.utils.OpenAICompatibleUtils

/** DeepSeek Provider using the Chat Completions API.
  *
  * Supports deepseek-chat (V3/V3.2 non-thinking) and deepseek-reasoner (V3.2
  * thinking). The API is OpenAI-compatible with additional fields for reasoning
  * content and cache stats.
  */
class DeepSeekProvider[F[_]: Async](
    client: Client[F],
    config: DeepSeekConfig
) extends LLMProvider[F]:

  val name: String = "deepseek"

  type Response = ChatCompletionResponse

  def chatCompletion(request: ChatCompletionRequest)
      : F[ChatCompletionResponse] =
    for
      // 1. Convert domain request to OpenAI format (DeepSeek is compatible)
      openAIRequest <- OpenAICompatibleUtils.toOpenAIRequest(request)

      // 2. Build HTTP request
      httpRequest = OpenAICompatibleUtils.buildHttpRequest(
        openAIRequest,
        config.baseUrl,
        config.apiKey
      )

      // 3. Execute HTTP call with proper error handling
      deepSeekResponse <- client.run(httpRequest).use { response =>
        given EntityDecoder[F, DeepSeekChatResponse] =
          jsonOf[F, DeepSeekChatResponse]
        response.status.code match
          case code if response.status.isSuccess =>
            response.as[DeepSeekChatResponse]
          case 429 =>
            val retryAfter = response.headers
              .get(CIString("retry-after"))
              .flatMap(_.head.value.toIntOption)
            response.as[String].flatMap { body =>
              Async[F].raiseError(RateLimitError(
                s"DeepSeek API rate limit exceeded: $body",
                retryAfter,
                Some(name)
              ))
            }
          case 401 | 403 =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(AuthenticationError(
                s"DeepSeek API authentication failed: $body",
                Some(name)
              ))
            }
          case 400 =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(InvalidRequestError(
                s"DeepSeek API invalid request: $body",
                Some(name)
              ))
            }
          case code if code >= 500 =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(ProviderUnavailableError(
                s"DeepSeek API server error: $body",
                Some(code),
                Some(name)
              ))
            }
          case code =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(new RuntimeException(
                s"DeepSeek API error ($code): $body"
              ))
            }
      }

      // 4. Convert to domain format
      response <- fromDeepSeekResponse(deepSeekResponse)
    yield response

  private def fromDeepSeekResponse(
      res: DeepSeekChatResponse
  ): F[ChatCompletionResponse] =
    Async[F].delay {
      ChatCompletionResponse(
        id = res.id,
        model = res.model,
        choices = res.choices.map { choice =>
          val message = choice.message.tool_calls match
            case Some(toolCalls) if toolCalls.nonEmpty =>
              val domainToolCalls = toolCalls.map { tc =>
                io.circe.parser.parse(tc.function.arguments) match
                  case Right(json) =>
                    ToolCall(
                      id = tc.id,
                      name = tc.function.name,
                      arguments = json
                    )
                  case Left(err) =>
                    throw new RuntimeException(
                      s"Failed to parse tool call arguments: ${err.getMessage}"
                    )
              }
              DomainMessage.Assistant(
                AssistantContent.ToolCalls(domainToolCalls)
              )
            case _ =>
              // For deepseek-reasoner, reasoning_content is available but we map
              // the final answer (content) as the text response. Reasoning content
              // is intentionally not surfaced in the domain model for now.
              DomainMessage.Assistant(
                AssistantContent.Text(choice.message.content.getOrElse(""))
              )

          ChatCompletionChoice(
            index = choice.index,
            message = message,
            finishReason = choice.finish_reason
          )
        },
        usage = Some(
          Usage(
            promptTokens = res.usage.prompt_tokens,
            completionTokens = res.usage.completion_tokens,
            totalTokens = res.usage.total_tokens
          )
        )
      )
    }

object DeepSeekProvider:
  def resource[F[_]: Async](
      config: DeepSeekConfig
  ): Resource[F, DeepSeekProvider[F]] =
    EmberClientBuilder.default[F].build.map { client =>
      new DeepSeekProvider[F](client, config)
    }

  def resourceFromEnv[F[_]: Async]: Resource[F, DeepSeekProvider[F]] =
    resource(DeepSeekConfig.fromEnv)

  def apply[F[_]: Async](
      client: Client[F],
      config: DeepSeekConfig
  ): DeepSeekProvider[F] =
    new DeepSeekProvider[F](client, config)
