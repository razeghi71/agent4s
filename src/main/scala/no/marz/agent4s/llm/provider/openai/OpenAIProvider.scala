package no.marz.agent4s.llm.provider.openai

import cats.effect.kernel.Async
import cats.effect.Resource
import cats.syntax.all._
import org.http4s._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.circe._
import org.http4s.headers._
import org.typelevel.ci.CIString
import io.circe.syntax._
import no.marz.agent4s.llm.LLMProvider
import no.marz.agent4s.llm.model.{Message => DomainMessage, _}
import no.marz.agent4s.llm.provider.openai.OpenAIModels.given

class OpenAIProvider[F[_]: Async](
  client: Client[F],
  config: OpenAIConfig
) extends LLMProvider[F]:

  def chatCompletion(request: ChatCompletionRequest): F[ChatCompletionResponse] =
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

  private def toOpenAIRequest(req: ChatCompletionRequest): F[OpenAIChatRequest] =
    Async[F].delay {
      OpenAIChatRequest(
        model = req.model,
        messages = req.messages.map(convertMessage),
        temperature = req.temperature,
        max_tokens = req.maxTokens,
        top_p = req.topP
      )
    }

  private def convertMessage(msg: DomainMessage): OpenAIMessage =
    msg match
      case DomainMessage.System(content) => 
        OpenAIMessage("system", content)
      case DomainMessage.User(content) => 
        OpenAIMessage("user", content)
      case DomainMessage.Assistant(AssistantContent.Text(value)) => 
        OpenAIMessage("assistant", value)
      case DomainMessage.Assistant(AssistantContent.ToolCalls()) =>
        throw new IllegalArgumentException("Tool calls not yet supported")
      case DomainMessage.Tool(_, _) => 
        throw new IllegalArgumentException("Tool messages not yet supported")

  private def buildHttpRequest(req: OpenAIChatRequest): Request[F] =
    val authHeader = Authorization(Credentials.Token(AuthScheme.Bearer, config.apiKey))
    val orgHeaders = config.organization
      .map(org => Header.Raw(CIString("OpenAI-Organization"), org))
      .map(h => Headers(h))
      .getOrElse(Headers.empty)
    
    Request[F](
      method = Method.POST,
      uri = Uri.unsafeFromString(s"${config.baseUrl}/chat/completions"),
      headers = Headers(authHeader, `Content-Type`(MediaType.application.json)) ++ orgHeaders
    ).withEntity(req.asJson)

  private def fromOpenAIResponse(res: OpenAIChatResponse): F[ChatCompletionResponse] =
    Async[F].delay {
      ChatCompletionResponse(
        id = res.id,
        model = res.model,
        choices = res.choices.map { choice =>
          ChatCompletionChoice(
            index = choice.index,
            message = DomainMessage.Assistant(AssistantContent.Text(choice.message.content)),
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
