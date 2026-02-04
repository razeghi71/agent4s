package no.marz.agent4s.llm.provider.perplexity

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
import no.marz.agent4s.llm.provider.perplexity.PerplexityModels.given
import no.marz.agent4s.llm.provider.utils.OpenAICompatibleUtils

class PerplexityProvider[F[_]: Async](
    client: Client[F],
    config: PerplexityConfig
) extends LLMProvider[F]:

  val name: String = "perplexity"

  type Response = ChatCompletionResponse & HasCitations

  /** Returns ChatCompletionResponse with HasCitations capability This uses
    * Scala 3 intersection types to add citations without subclassing
    */
  def chatCompletion(
      request: ChatCompletionRequest
  ): F[ChatCompletionResponse & HasCitations] =
    for
      // 1. Convert domain request to OpenAI format (Perplexity is compatible)
      openAIRequest <- OpenAICompatibleUtils.toOpenAIRequest(request)

      // 2. Build HTTP request
      httpRequest = OpenAICompatibleUtils.buildHttpRequest(
        openAIRequest,
        config.baseUrl,
        config.apiKey
      )

      // 3. Execute HTTP call with proper error handling
      perplexityResponse <- client.run(httpRequest).use { response =>
        given EntityDecoder[F, PerplexityChatResponse] =
          jsonOf[F, PerplexityChatResponse]
        response.status.code match
          case code if response.status.isSuccess =>
            response.as[PerplexityChatResponse]
          case 429 =>
            val retryAfter = response.headers
              .get(CIString("retry-after"))
              .flatMap(_.head.value.toIntOption)
            response.as[String].flatMap { body =>
              Async[F].raiseError(RateLimitError(
                s"Perplexity API rate limit exceeded: $body",
                retryAfter,
                Some(name)
              ))
            }
          case 401 | 403 =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(AuthenticationError(
                s"Perplexity API authentication failed: $body",
                Some(name)
              ))
            }
          case 400 =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(InvalidRequestError(
                s"Perplexity API invalid request: $body",
                Some(name)
              ))
            }
          case code if code >= 500 =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(ProviderUnavailableError(
                s"Perplexity API server error: $body",
                Some(code),
                Some(name)
              ))
            }
          case code =>
            response.as[String].flatMap { body =>
              Async[F].raiseError(new RuntimeException(
                s"Perplexity API error ($code): $body"
              ))
            }
      }

      // 4. Convert to domain format with citations
      response <- fromPerplexityResponse(perplexityResponse)
    yield response

  private def fromPerplexityResponse(
      res: PerplexityChatResponse
  ): F[ChatCompletionResponse & HasCitations] =
    Async[F].delay {
      // First convert to base ChatCompletionResponse
      val baseResponse = ChatCompletionResponse(
        id = res.id,
        model = res.model,
        choices = res.choices.map { choice =>
          val message = choice.message.tool_calls match
            case Some(toolCalls) =>
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
            case None =>
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

      // Parse citations from Perplexity response
      val parsedCitations = res.citations
        .getOrElse(Nil)
        .map { url =>
          Citation(url = url, title = None)
        }

      // Create an object that is BOTH ChatCompletionResponse AND HasCitations
      new ChatCompletionResponse(
        baseResponse.id,
        baseResponse.model,
        baseResponse.choices,
        baseResponse.usage
      ) with HasCitations:
        def citations: Seq[Citation] = parsedCitations
    }

object PerplexityProvider:
  def resource[F[_]: Async](
      config: PerplexityConfig
  ): Resource[F, PerplexityProvider[F]] =
    EmberClientBuilder.default[F].build.map { client =>
      new PerplexityProvider[F](client, config)
    }

  def resourceFromEnv[F[_]: Async]: Resource[F, PerplexityProvider[F]] =
    resource(PerplexityConfig.fromEnv)
