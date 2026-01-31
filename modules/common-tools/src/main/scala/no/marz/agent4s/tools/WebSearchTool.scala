package no.marz.agent4s.tools

import cats.effect.kernel.Async
import cats.syntax.all.*
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import no.marz.agent4s.llm.LLMProvider
import no.marz.agent4s.llm.model.{
  Tool, ChatCompletionRequest, Message, HasCitations, ChatCompletionResponse
}

case class WebSearchInput(
    query: String
)

case class SearchSource(
    url: String,
    title: Option[String] = None
)

case class WebSearchOutput(
    answer: String,
    sources: List[SearchSource]
)

object WebSearchInput:
  given Decoder[WebSearchInput] = deriveDecoder[WebSearchInput]
  given Encoder[WebSearchInput] = deriveEncoder[WebSearchInput]

object SearchSource:
  given Encoder[SearchSource] = deriveEncoder[SearchSource]

object WebSearchOutput:
  given Encoder[WebSearchOutput] = deriveEncoder[WebSearchOutput]

/** Web search tool that works with any LLM provider returning responses with
  * citations
  *
  * @tparam F
  *   Effect type
  * @param provider
  *   Any LLM provider whose response type includes HasCitations
  * @param searchModel
  *   The model identifier to use for search
  */
class WebSearchTool[
    F[_]: Async,
    R <: ChatCompletionResponse & HasCitations
] private (
    provider: LLMProvider[F] { type Response = R },
    searchModel: String
) extends Tool[F, WebSearchInput, WebSearchOutput]:

  def name: String = "web_search"

  def description: String =
    """Search the internet for current, real-time information. 
      |Use this tool when you need up-to-date facts, news, or information not in your training data.
      |The search returns an answer synthesized from multiple web sources along with citations.""".stripMargin

  def execute(input: WebSearchInput): F[WebSearchOutput] =
    val request = ChatCompletionRequest(
      model = searchModel,
      messages = List(Message.User(input.query)),
      tools = Set.empty,
      temperature = None,
      maxTokens = None,
      topP = None
    )

    provider.chatCompletion(request).map { response =>
      // Response type is guaranteed to be R which extends HasCitations
      val answer = response.choices.headOption
        .flatMap { choice =>
          choice.message match
            case Message.Assistant(content) =>
              content match
                case no.marz.agent4s.llm.model.AssistantContent.Text(text) =>
                  Some(text)
                case _ => None
            case _ => None
        }
        .getOrElse("No answer available")

      val sources = response.citations.map { citation =>
        SearchSource(url = citation.url, title = citation.title)
      }.toList

      WebSearchOutput(answer = answer, sources = sources)
    }

object WebSearchTool:
  /** Creates a WebSearchTool with type inference.
    *
    * Example:
    * {{{
    * val perplexityProvider = PerplexityProvider[IO](config)
    * val searchTool = WebSearchTool(perplexityProvider, "sonar")
    * }}}
    *
    * @param provider
    *   Any LLM provider whose response type includes HasCitations
    * @param searchModel
    *   The model identifier to use for search
    */
  def apply[F[_]: Async, R <: ChatCompletionResponse & HasCitations](
      provider: LLMProvider[F] { type Response = R },
      searchModel: String
  ): WebSearchTool[F, R] =
    new WebSearchTool[F, R](provider, searchModel)
