package no.marz.agent4s.llm

import cats.MonadError
import cats.syntax.all.*
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*
import no.marz.agent4s.llm.model.*

class ToolRegistry[F[_]] private (
    private val tools: Map[String, ToolRegistry.ToolEntry[F]]
)(using F: MonadError[F, Throwable]):

  def register[I: Decoder, O: Encoder](
      tool: Tool[F, I, O]
  ): ToolRegistry[F] =
    if tools.contains(tool.name) then
      throw new IllegalArgumentException(
        s"Tool '${tool.name}' is already registered"
      )

    val executor: Json => F[Json] = (args: Json) =>
      args.as[I] match
        case Right(input) =>
          tool.execute(input).map(_.asJson)
        case Left(err) =>
          F.raiseError(
            new RuntimeException(
              s"Failed to decode tool arguments for '${tool.name}': ${err.getMessage}"
            )
          )

    val entry = ToolRegistry.ToolEntry(tool.toToolSchema, executor)
    new ToolRegistry[F](tools + (tool.name -> entry))

  def execute(
      toolCall: ToolCall,
      returnErrorsAsToolMessages: Boolean = true
  ): F[Message.Tool] =
    val result = tools.get(toolCall.name) match
      case Some(entry) => entry.executor(toolCall.arguments)
      case None        =>
        F.raiseError(
          new RuntimeException(s"Tool not found: '${toolCall.name}'")
        )

    val successMessage = result.map { json =>
      Message.Tool(
        toolCallId = toolCall.id,
        toolName = toolCall.name,
        content = json.noSpaces
      )
    }

    if returnErrorsAsToolMessages then
      successMessage.handleErrorWith { err =>
        F.pure(
          Message.Tool(
            toolCallId = toolCall.id,
            toolName = toolCall.name,
            content = Json
              .obj(
                "error" -> Json.fromString(err.getMessage),
                "type" -> Json.fromString(err.getClass.getSimpleName)
              )
              .noSpaces
          )
        )
      }
    else successMessage

  def getSchemas: Set[ToolSchema] =
    tools.values.map(_.schema).toSet

object ToolRegistry:
  private case class ToolEntry[F[_]](
      schema: ToolSchema,
      executor: Json => F[Json]
  )

  def empty[F[_]](using MonadError[F, Throwable]): ToolRegistry[F] =
    new ToolRegistry[F](Map.empty)

  extension (toolCall: ToolCall)
    def execute[F[_]](returnErrorsAsToolMessages: Boolean = true)(using
        registry: ToolRegistry[F]
    ): F[Message.Tool] =
      registry.execute(toolCall, returnErrorsAsToolMessages)
