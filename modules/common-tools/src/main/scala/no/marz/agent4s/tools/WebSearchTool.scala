package no.marz.agent4s.tools

import cats.effect.kernel.Async
import cats.syntax.all.*
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import no.marz.agent4s.llm.model.{Tool, ChatCompletionRequest, Message, HasCitations}
import no.marz.agent4s.llm.provider.perplexity.PerplexityProvider

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

/**
 * Web search tool using Perplexity's Sonar model
 * 
 * This tool requires a provider that returns responses with citations (HasCitations capability)
 * The type constraint `chatCompletion` ensures at compile time that the provider supports citations
 */
class WebSearchTool[F[_]: Async](
    provider: PerplexityProvider[F]
) extends Tool[F, WebSearchInput, WebSearchOutput]:

  def name: String = "web_search"

  def description: String =
    """Search the internet for current, real-time information. 
      |Use this tool when you need up-to-date facts, news, or information not in your training data.
      |The search returns an answer synthesized from multiple web sources along with citations.""".stripMargin

  def execute(input: WebSearchInput): F[WebSearchOutput] =
    val request = ChatCompletionRequest(
      model = "sonar",
      messages = List(Message.User(input.query)),
      tools = Set.empty,
      temperature = None,
      maxTokens = None,
      topP = None
    )

    provider.chatCompletion(request).map { response =>
      // response is ChatCompletionResponse & HasCitations
      // We can access both base fields and citations
      val answer = response.choices.headOption
        .flatMap { choice =>
          choice.message match
            case Message.Assistant(content) =>
              content match
                case no.marz.agent4s.llm.model.AssistantContent.Text(text) => Some(text)
                case _ => None
            case _ => None
        }
        .getOrElse("No answer available")

      // Access citations through HasCitations trait
      val sources = response.citations.map { citation =>
        SearchSource(url = citation.url, title = citation.title)
      }.toList

      WebSearchOutput(answer = answer, sources = sources)
    }
