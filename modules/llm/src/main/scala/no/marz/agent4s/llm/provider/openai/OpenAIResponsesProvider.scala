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
import io.circe.Json
import no.marz.agent4s.llm.LLMProvider
import no.marz.agent4s.llm.model.{Message as DomainMessage, *}
import no.marz.agent4s.llm.provider.openai.ResponsesModels.given

/** OpenAI Responses Provider using the Responses API.
  * 
  * This provider supports GPT-5, GPT-5.2, o3, o4-mini and other models
  * that require the new Responses API (POST /v1/responses).
  * 
  * For older models like GPT-4o, GPT-4, GPT-3.5-turbo, use OpenAICompletionProvider.
  */
class OpenAIResponsesProvider[F[_]: Async](
    client: Client[F],
    config: OpenAIConfig
) extends LLMProvider[F]:

  type Response = ChatCompletionResponse

  def chatCompletion(request: ChatCompletionRequest): F[ChatCompletionResponse] =
    for
      // 1. Convert domain request to Responses API format
      responsesRequest <- toResponsesRequest(request)

      // 2. Build HTTP request
      httpRequest = buildHttpRequest(responsesRequest)

      // 3. Execute HTTP call and decode response with error handling
      responsesResponse <- client.run(httpRequest).use { response =>
        given EntityDecoder[F, ResponsesResponse] = jsonOf[F, ResponsesResponse]
        if response.status.isSuccess then
          response.as[ResponsesResponse]
        else
          response.as[String].flatMap { body =>
            Async[F].raiseError(new RuntimeException(
              s"OpenAI Responses API error (${response.status}): $body"
            ))
          }
      }

      // 4. Convert Responses API response back to domain format
      response <- fromResponsesResponse(responsesResponse)
    yield response

  /** Convert domain ChatCompletionRequest to Responses API format */
  private def toResponsesRequest(req: ChatCompletionRequest): F[ResponsesRequest] =
    Async[F].delay {
      // Extract system instruction from messages
      val systemInstruction = req.messages.collectFirst {
        case DomainMessage.System(content) => content
      }

      // Convert non-system messages to Responses API input items
      // For Responses API, we need to include function_call items before function_call_output items
      val inputItems = req.messages.flatMap {
        case DomainMessage.System(_) => None // Already extracted as instruction
        case DomainMessage.User(content) =>
          Some(ResponsesInputItem.Message(role = "user", content = content))
        case DomainMessage.Assistant(AssistantContent.Text(value)) =>
          Some(ResponsesInputItem.Message(role = "assistant", content = value))
        case DomainMessage.Assistant(AssistantContent.ToolCalls(calls)) =>
          // Convert each tool call to a function_call input item
          // The Responses API needs these to be present before function_call_output items
          calls.map { call =>
            ResponsesInputItem.FunctionCall(
              call_id = call.id,
              name = call.name,
              arguments = call.arguments.noSpaces
            )
          }
        case DomainMessage.Tool(toolCallId, _, content) =>
          Some(ResponsesInputItem.FunctionCallOutput(call_id = toolCallId, output = content))
      }

      // Determine input format
      val input: ResponsesInput = inputItems.toList match
        case Nil => ResponsesInput.Text("")
        case List(ResponsesInputItem.Message(_, content)) if systemInstruction.isEmpty =>
          // Simple case: single user message, use text format
          ResponsesInput.Text(content)
        case items =>
          // Complex case: multiple items or mixed with system instruction
          ResponsesInput.Items(items)

      // Convert tools to Responses API format (flattened structure)
      val tools = if req.tools.nonEmpty then
        Some(req.tools.toSeq.map { toolSchema =>
          ResponsesTool(
            `type` = "function",
            name = toolSchema.name,
            description = toolSchema.description,
            parameters = toolSchema.parameters,
            strict = None // Don't use strict mode - it requires all fields in 'required'
          )
        })
      else
        None

      ResponsesRequest(
        model = req.model,
        input = input,
        instructions = systemInstruction,
        tools = tools,
        temperature = req.temperature,
        max_output_tokens = req.maxTokens,
        top_p = req.topP
      )
    }

  /** Build HTTP request for Responses API */
  private def buildHttpRequest(req: ResponsesRequest): Request[F] =
    val authHeader = Authorization(Credentials.Token(AuthScheme.Bearer, config.apiKey))
    
    val orgHeaders = config.organization
      .map(org => Header.Raw(CIString("OpenAI-Organization"), org))
      .map(h => Headers(h))
      .getOrElse(Headers.empty)

    Request[F](
      method = Method.POST,
      uri = Uri.unsafeFromString(s"${config.baseUrl}/responses"),
      headers = Headers(authHeader, `Content-Type`(MediaType.application.json)) ++ orgHeaders
    ).withEntity(req.asJson)

  /** Convert Responses API response to domain ChatCompletionResponse */
  private def fromResponsesResponse(res: ResponsesResponse): F[ChatCompletionResponse] =
    Async[F].delay {
      // Check for errors
      res.error.foreach { err =>
        throw new RuntimeException(s"OpenAI Responses API error: ${err.code} - ${err.message}")
      }

      // Extract function calls and messages from output
      val functionCalls = res.output.collect {
        case ResponsesOutputItem.FunctionCall(_, callId, name, arguments) =>
          io.circe.parser.parse(arguments) match
            case Right(json) =>
              ToolCall(id = callId, name = name, arguments = json)
            case Left(err) =>
              throw new RuntimeException(s"Failed to parse function call arguments: ${err.getMessage}")
      }

      val textContent = res.output.collect {
        case ResponsesOutputItem.Message(_, _, content) =>
          content.collect {
            case MessageContent.OutputText(text) => text
          }.mkString
      }.mkString

      // Create the domain message
      val message: DomainMessage = 
        if functionCalls.nonEmpty then
          DomainMessage.Assistant(AssistantContent.ToolCalls(functionCalls))
        else
          DomainMessage.Assistant(AssistantContent.Text(textContent))

      // Determine finish reason
      val finishReason = 
        if functionCalls.nonEmpty then Some("tool_calls")
        else if res.status == "completed" then Some("stop")
        else Some(res.status)

      ChatCompletionResponse(
        id = res.id,
        model = res.model,
        choices = Seq(
          ChatCompletionChoice(
            index = 0,
            message = message,
            finishReason = finishReason
          )
        ),
        usage = res.usage.map { u =>
          Usage(
            promptTokens = u.input_tokens,
            completionTokens = u.output_tokens,
            totalTokens = u.total_tokens
          )
        }
      )
    }

object OpenAIResponsesProvider:
  /** Create a provider resource with the given config */
  def resource[F[_]: Async](config: OpenAIConfig): Resource[F, LLMProvider[F]] =
    EmberClientBuilder.default[F].build.map { client =>
      new OpenAIResponsesProvider[F](client, config)
    }

  /** Create a provider resource using environment variables */
  def resourceFromEnv[F[_]: Async]: Resource[F, LLMProvider[F]] =
    resource(OpenAIConfig.fromEnv)

  /** Create a provider with a custom HTTP client */
  def apply[F[_]: Async](client: Client[F], config: OpenAIConfig): OpenAIResponsesProvider[F] =
    new OpenAIResponsesProvider[F](client, config)
