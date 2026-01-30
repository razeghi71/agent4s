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

class OpenAIProvider[F[_]: Async](
    client: Client[F],
    config: OpenAIConfig
) extends LLMProvider[F]:

  def chatCompletion(request: ChatCompletionRequest)
      : F[ChatCompletionResponse] =
    for
      // 1. Convert domain request to OpenAI format
      openAIRequest <- toOpenAIRequest(request)

      // 2. Build HTTP request
      httpRequest = buildHttpRequest(openAIRequest)

      // 3. Execute HTTP call and decode response
      openAIResponse <- client.expect[OpenAIChatResponse](httpRequest)(using
        jsonOf[F, OpenAIChatResponse]
      )

      // 4. Convert OpenAI response back to domain format
      response <- fromOpenAIResponse(openAIResponse)
    yield response

  private def toOpenAIRequest(req: ChatCompletionRequest)
      : F[OpenAIChatRequest] =
    Async[F].delay {
      val openAITools = if req.tools.nonEmpty then
        Some(req.tools.toSeq.map { toolSchema =>
          OpenAITool(
            `type` = "function",
            function = OpenAIFunction(
              name = toolSchema.name,
              description = toolSchema.description,
              parameters = toolSchema.parameters
            )
          )
        })
      else
        None

      OpenAIChatRequest(
        model = req.model,
        messages = req.messages.map(convertMessage),
        tools = openAITools,
        temperature = req.temperature,
        max_tokens = req.maxTokens,
        top_p = req.topP
      )
    }

  private def convertMessage(msg: DomainMessage): OpenAIMessage =
    msg match
      case DomainMessage.System(content) =>
        OpenAIMessage(role = "system", content = Some(content))
      case DomainMessage.User(content) =>
        OpenAIMessage(role = "user", content = Some(content))
      case DomainMessage.Assistant(AssistantContent.Text(value)) =>
        OpenAIMessage(role = "assistant", content = Some(value))
      case DomainMessage.Assistant(AssistantContent.ToolCalls(calls)) =>
        OpenAIMessage(
          role = "assistant",
          content = None,
          tool_calls = Some(calls.map { call =>
            OpenAIToolCall(
              id = call.id,
              `type` = "function",
              function = OpenAIFunctionCall(
                name = call.name,
                arguments = call.arguments.noSpaces
              )
            )
          })
        )
      case DomainMessage.Tool(toolCallId, toolName, content) =>
        OpenAIMessage(
          role = "tool",
          content = Some(content),
          tool_call_id = Some(toolCallId),
          name = Some(toolName)
        )

  private def buildHttpRequest(req: OpenAIChatRequest): Request[F] =
    val authHeader =
      Authorization(Credentials.Token(AuthScheme.Bearer, config.apiKey))
    val orgHeaders = config.organization
      .map(org => Header.Raw(CIString("OpenAI-Organization"), org))
      .map(h => Headers(h))
      .getOrElse(Headers.empty)

    Request[F](
      method = Method.POST,
      uri = Uri.unsafeFromString(s"${config.baseUrl}/chat/completions"),
      headers =
        Headers(authHeader, `Content-Type`(MediaType.application.json)) ++
          orgHeaders
    ).withEntity(req.asJson)

  private def fromOpenAIResponse(res: OpenAIChatResponse)
      : F[ChatCompletionResponse] =
    Async[F].delay {
      ChatCompletionResponse(
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
        usage = Some(Usage(
          promptTokens = res.usage.prompt_tokens,
          completionTokens = res.usage.completion_tokens,
          totalTokens = res.usage.total_tokens
        ))
      )
    }

object OpenAIProvider:
  def resource[F[_]: Async](config: OpenAIConfig): Resource[F, LLMProvider[F]] =
    EmberClientBuilder.default[F].build.map { client =>
      new OpenAIProvider[F](client, config)
    }

  def resourceFromEnv[F[_]: Async]: Resource[F, LLMProvider[F]] =
    resource(OpenAIConfig.fromEnv)
