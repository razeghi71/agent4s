package no.marz.agent4s.llm.model

case class ChatCompletionResponse(
    id: String,
    model: String,
    choices: Seq[ChatCompletionChoice],
    usage: Option[Usage]
)

case class ChatCompletionChoice(
    index: Int,
    message: Message,
    finishReason: Option[String]
)

case class Usage(
    promptTokens: Int,
    completionTokens: Int,
    totalTokens: Int
)

case class Citation(
    url: String,
    title: Option[String] = None
)

trait HasCitations:
  def citations: Seq[Citation]
