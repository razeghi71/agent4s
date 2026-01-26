package no.marz.agent4s.llm.model

case class ChatCompletionRequest(
    model: String,
    messages: Seq[Message],
    temperature: Option[Double] = None,
    maxTokens: Option[Int] = None,
    topP: Option[Double] = None
//    tools: Option[Seq[ToolDefinition]] = None
)
