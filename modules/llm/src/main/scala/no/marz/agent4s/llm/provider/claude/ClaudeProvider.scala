package no.marz.agent4s.llm.provider.claude

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
import no.marz.agent4s.llm.provider.claude.ClaudeModels.given

/** Rate limit error with retry information.
  * 
  * Thrown when Claude API returns 429. Contains optional retry-after hint
  * from the API response headers.
  * 
  * Users can catch this and implement their own retry logic:
  * {{{
  * provider.chatCompletion(request).handleErrorWith {
  *   case RateLimitException(msg, Some(seconds)) =>
  *     IO.sleep(seconds.seconds) >> provider.chatCompletion(request)
  *   case RateLimitException(msg, None) =>
  *     IO.sleep(1.second) >> provider.chatCompletion(request)
  * }
  * }}}
  */
case class RateLimitException(
    message: String,
    retryAfterSeconds: Option[Int]
) extends RuntimeException(message)

/** Claude Provider using the Anthropic Messages API.
  *
  * This provider supports Claude models (claude-sonnet-4-5, claude-opus-4, etc.)
  * via the Messages API (POST /v1/messages).
  *
  * Key features:
  * - System prompt is a top-level parameter, not a message
  * - Uses `x-api-key` header instead of Bearer token
  * - Tool definitions use `input_schema` instead of `parameters`
  * - Tool results are content blocks in user messages, not separate messages
  * - `max_tokens` is required
  * - Prompt caching support for reduced costs and rate limits
  * - Throws RateLimitException on 429 with retry-after hint (no automatic retry)
  */
class ClaudeProvider[F[_]: Async](
    client: Client[F],
    config: ClaudeConfig
) extends LLMProvider[F]:

  type Response = ChatCompletionResponse

  def chatCompletion(request: ChatCompletionRequest): F[ChatCompletionResponse] =
    for
      // 1. Convert domain request to Claude API format
      claudeRequest <- toClaudeRequest(request)

      // 2. Build HTTP request
      httpRequest = buildHttpRequest(claudeRequest)

      // 3. Execute HTTP call with error handling
      claudeResponse <- client.run(httpRequest).use { response =>
        given EntityDecoder[F, ClaudeResponse] = jsonOf[F, ClaudeResponse]
        if response.status.isSuccess then
          response.as[ClaudeResponse]
        else if response.status.code == 429 then
          // Rate limit - extract retry-after header and throw exception
          val retryAfter = response.headers
            .get(CIString("retry-after"))
            .flatMap(_.head.value.toIntOption)
          response.as[String].flatMap { body =>
            Async[F].raiseError(RateLimitException(
              s"Claude API rate limit exceeded: $body",
              retryAfter
            ))
          }
        else
          response.as[String].flatMap { body =>
            Async[F].raiseError(new RuntimeException(
              s"Claude API error (${response.status}): $body"
            ))
          }
      }

      // 4. Convert Claude response to domain format
      response <- fromClaudeResponse(claudeResponse)
    yield response

  /** Convert domain ChatCompletionRequest to Claude API format */
  private def toClaudeRequest(req: ChatCompletionRequest): F[ClaudeRequest] =
    Async[F].delay {
      // Extract system prompt from messages (Claude uses top-level system param)
      // If caching is enabled, wrap in blocks with cache_control
      val systemPrompt: Option[ClaudeSystemContent] = req.messages.collectFirst {
        case DomainMessage.System(content) => 
          if config.enablePromptCaching then
            ClaudeSystemContent.Blocks(Seq(
              ClaudeSystemBlock("text", content, Some(CacheControl.ephemeral))
            ))
          else
            ClaudeSystemContent.Text(content)
      }

      // Convert messages, handling tool calls and results specially
      val claudeMessages = convertMessages(req.messages.filterNot {
        case DomainMessage.System(_) => true
        case _ => false
      })

      // Convert tools to Claude format, with cache control on the last tool
      val tools = if req.tools.nonEmpty then
        val toolSeq = req.tools.toSeq
        Some(toolSeq.zipWithIndex.map { case (toolSchema, idx) =>
          val isLast = idx == toolSeq.size - 1
          ClaudeTool(
            name = toolSchema.name,
            description = toolSchema.description,
            input_schema = toolSchema.parameters,
            cache_control = if config.enablePromptCaching && isLast then Some(CacheControl.ephemeral) else None
          )
        })
      else
        None

      ClaudeRequest(
        model = req.model,
        max_tokens = req.maxTokens.getOrElse(config.defaultMaxTokens),
        messages = claudeMessages,
        system = systemPrompt,
        tools = tools,
        temperature = req.temperature,
        top_p = req.topP
      )
    }

  /** Convert domain messages to Claude format.
    * 
    * Key differences:
    * - Tool calls from assistant become ToolUse content blocks
    * - Tool results become ToolResult content blocks in the next user message
    * - Consecutive same-role messages need to be merged
    */
  private def convertMessages(messages: Seq[DomainMessage]): Seq[ClaudeMessage] =
    // Group consecutive messages and tool results appropriately
    val result = scala.collection.mutable.ListBuffer[ClaudeMessage]()
    var pendingToolResults = scala.collection.mutable.ListBuffer[ClaudeContentBlock.ToolResult]()

    messages.foreach {
      case DomainMessage.User(content) =>
        // If we have pending tool results, add them first as a user message
        if pendingToolResults.nonEmpty then
          result += ClaudeMessage("user", ClaudeContent.Blocks(pendingToolResults.toSeq))
          pendingToolResults.clear()
        result += ClaudeMessage("user", ClaudeContent.Text(content))

      case DomainMessage.Assistant(AssistantContent.Text(text)) =>
        // Flush pending tool results before assistant message
        if pendingToolResults.nonEmpty then
          result += ClaudeMessage("user", ClaudeContent.Blocks(pendingToolResults.toSeq))
          pendingToolResults.clear()
        result += ClaudeMessage("assistant", ClaudeContent.Text(text))

      case DomainMessage.Assistant(AssistantContent.ToolCalls(calls)) =>
        // Flush pending tool results before assistant message
        if pendingToolResults.nonEmpty then
          result += ClaudeMessage("user", ClaudeContent.Blocks(pendingToolResults.toSeq))
          pendingToolResults.clear()
        // Convert tool calls to ToolUse blocks
        val toolUseBlocks = calls.map { call =>
          ClaudeContentBlock.ToolUse(
            id = call.id,
            name = call.name,
            input = call.arguments
          )
        }
        result += ClaudeMessage("assistant", ClaudeContent.Blocks(toolUseBlocks.toSeq))

      case DomainMessage.Tool(toolCallId, _, content) =>
        // Accumulate tool results - they'll be sent in a user message
        pendingToolResults += ClaudeContentBlock.ToolResult(
          tool_use_id = toolCallId,
          content = content
        )

      case _ => // Skip system messages (handled separately)
    }

    // Flush any remaining tool results
    if pendingToolResults.nonEmpty then
      result += ClaudeMessage("user", ClaudeContent.Blocks(pendingToolResults.toSeq))

    result.toSeq

  /** Build HTTP request for Claude API */
  private def buildHttpRequest(req: ClaudeRequest): Request[F] =
    Request[F](
      method = Method.POST,
      uri = Uri.unsafeFromString(s"${config.baseUrl}/messages"),
      headers = Headers(
        Header.Raw(CIString("x-api-key"), config.apiKey),
        Header.Raw(CIString("anthropic-version"), config.anthropicVersion),
        `Content-Type`(MediaType.application.json)
      )
    ).withEntity(req.asJson)

  /** Convert Claude response to domain ChatCompletionResponse */
  private def fromClaudeResponse(res: ClaudeResponse): F[ChatCompletionResponse] =
    Async[F].delay {
      // Extract tool uses and text from content blocks
      val toolCalls = res.content.collect {
        case ClaudeContentBlock.ToolUse(id, name, input) =>
          ToolCall(id = id, name = name, arguments = input)
      }

      val textContent = res.content.collect {
        case ClaudeContentBlock.Text(text, _) => text
      }.mkString

      // Create the domain message
      val message: DomainMessage =
        if toolCalls.nonEmpty then
          DomainMessage.Assistant(AssistantContent.ToolCalls(toolCalls))
        else
          DomainMessage.Assistant(AssistantContent.Text(textContent))

      // Map stop_reason to finish_reason
      val finishReason = res.stop_reason.map {
        case "end_turn" => "stop"
        case "tool_use" => "tool_calls"
        case "max_tokens" => "length"
        case "stop_sequence" => "stop"
        case other => other
      }

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
        usage = Some(
          Usage(
            promptTokens = res.usage.input_tokens,
            completionTokens = res.usage.output_tokens,
            totalTokens = res.usage.input_tokens + res.usage.output_tokens
          )
        )
      )
    }

object ClaudeProvider:
  /** Create a provider resource with the given config */
  def resource[F[_]: Async](config: ClaudeConfig): Resource[F, LLMProvider[F]] =
    EmberClientBuilder.default[F].build.map { client =>
      new ClaudeProvider[F](client, config)
    }

  /** Create a provider resource using environment variables */
  def resourceFromEnv[F[_]: Async]: Resource[F, LLMProvider[F]] =
    resource(ClaudeConfig.fromEnv)

  /** Create a provider with a custom HTTP client */
  def apply[F[_]: Async](client: Client[F], config: ClaudeConfig): ClaudeProvider[F] =
    new ClaudeProvider[F](client, config)
