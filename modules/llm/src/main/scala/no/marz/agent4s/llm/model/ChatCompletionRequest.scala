package no.marz.agent4s.llm.model

case class ChatCompletionRequest(
    model: String,
    messages: Seq[Message],
    tools: Set[ToolSchema] = Set.empty,
    temperature: Option[Double] = None,
    maxTokens: Option[Int] = None,
    topP: Option[Double] = None
):
  // Validate that tool names are unique (Set dedupes by equality, but we want name-only uniqueness)
  require(
    tools.groupBy(_.name).forall(_._2.size == 1), {
      val duplicates = tools.groupBy(_.name).filter(_._2.size > 1).keys
      s"Duplicate tool names found: ${duplicates.mkString(", ")}"
    }
  )
