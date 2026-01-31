package no.marz.agent4s.llm.provider.utils

import cats.effect.kernel.Async
import cats.syntax.all.*
import org.http4s.*
import org.http4s.headers.*
import org.http4s.circe.*
import io.circe.syntax.*
import io.circe.Json
import no.marz.agent4s.llm.model.{Message as DomainMessage, *}
import no.marz.agent4s.llm.provider.openai.*
import no.marz.agent4s.llm.provider.openai.OpenAIModels.given

object OpenAICompatibleUtils:

  /** Convert domain ChatCompletionRequest to OpenAI format */
  def toOpenAIRequest[F[_]: Async](
      req: ChatCompletionRequest
  ): F[OpenAIChatRequest] =
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

  /** Convert domain Message to OpenAI format */
  def convertMessage(msg: DomainMessage): OpenAIMessage =
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

  /** Build HTTP request with bearer token authorization */
  def buildHttpRequest[F[_]](
      apiRequest: OpenAIChatRequest,
      baseUrl: String,
      apiKey: String,
      additionalHeaders: Headers = Headers.empty
  )(using Async[F]): Request[F] =
    val authHeader = Authorization(Credentials.Token(AuthScheme.Bearer, apiKey))

    Request[F](
      method = Method.POST,
      uri = Uri.unsafeFromString(s"$baseUrl/chat/completions"),
      headers =
        Headers(authHeader, `Content-Type`(MediaType.application.json)) ++
          additionalHeaders
    ).withEntity(apiRequest.asJson)

  /** Convert OpenAI response to domain ChatCompletionResponse */
  def fromOpenAIResponse[F[_]: Async](
      res: OpenAIChatResponse
  ): F[ChatCompletionResponse] =
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
        usage = Some(
          Usage(
            promptTokens = res.usage.prompt_tokens,
            completionTokens = res.usage.completion_tokens,
            totalTokens = res.usage.total_tokens
          )
        )
      )
    }
