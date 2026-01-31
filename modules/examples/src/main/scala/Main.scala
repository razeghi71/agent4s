import cats.effect.IO
import cats.effect.unsafe.implicits.global
import no.marz.agent4s.graph.*
import no.marz.agent4s.llm.model.{
  Tool,
  ToolMetadata,
  ToolSchema,
  ChatCompletionRequest,
  ChatCompletionResponse,
  HasCitations,
  Message,
  AssistantContent,
  ToolCall
}
import no.marz.agent4s.llm.{LLMProvider, ToolRegistry}
import no.marz.agent4s.llm.ToolRegistry.execute
import no.marz.agent4s.llm.provider.openai.OpenAIProvider
import no.marz.agent4s.llm.provider.perplexity.PerplexityProvider
import no.marz.agent4s.tools.{WebSearchTool, WebSearchInput, WebSearchOutput, SearchSource}
import com.melvinlow.json.schema.annotation.description
import com.melvinlow.json.schema.generic.auto.given
import io.circe.generic.auto.given
import cats.syntax.all.*

case class AgentState(
    messages: List[Message]
) extends GraphState:
  def hasToolCalls: Boolean = messages.headOption match
    case Some(Message.Assistant(AssistantContent.ToolCalls(_))) => true
    case _                                                      => false

  def getToolCalls: List[ToolCall] = messages.headOption match
    case Some(Message.Assistant(AssistantContent.ToolCalls(calls))) =>
      calls.toList
    case _ => List.empty

object StartNode extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] = IO.pure(state)

class ChatNode(llmProvider: LLMProvider[IO], toolRegistry: ToolRegistry[IO])
    extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] =
    val request = ChatCompletionRequest(
      model = "gpt-4o-mini",
      messages = state.messages.reverse, // Reverse to chronological order
      tools = toolRegistry.getSchemas
    )

    llmProvider.chatCompletion(request).map { response =>
      val assistantMessage = response.choices.headOption
        .map(_.message)
        .getOrElse(Message.Assistant(AssistantContent.Text("No response")))

      state.copy(messages = assistantMessage :: state.messages)
    }

class ToolNode(using toolRegistry: ToolRegistry[IO])
    extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] =
    val toolCalls = state.getToolCalls

    toolCalls.traverse { toolCall =>
      toolCall.execute(returnErrorsAsToolMessages = true)
    }.map { toolMessages =>
      // Add all tool result messages to state
      state.copy(messages = toolMessages.reverse ::: state.messages)
    }

enum WeatherUnit:
  case C, F

case class Address(street: String, city: String)

case class GetWeatherInput(
    @description("The city and state, e.g. San Francisco, CA")
    location: String,
    @description("centigrade or fahrenheit")
    unit: Option[WeatherUnit],
    @description("Optional address")
    address: Option[Address],
    @description("Optional tags")
    tags: Option[List[String]]
)
case class GetWeatherOutput(value: Float, unit: String)

object GetWeatherTool extends Tool[IO, GetWeatherInput, GetWeatherOutput]:
  def name: String = "GetWeather"
  def description: String =
    "A Tool that given a location and unit returns the degree in that unit"

  def execute(input: GetWeatherInput): IO[GetWeatherOutput] =
    IO.pure(GetWeatherOutput(22.5f, input.unit.fold("C")(_.toString)))

@main def hello(): Unit =
  val result = (
    OpenAIProvider.resourceFromEnv[IO],
    PerplexityProvider.resourceFromEnv[IO]
  ).tupled.use { case (openAIProvider, perplexityProvider) =>
    // Setup tool registry with both weather and web search tools
    // WebSearchTool accepts any provider whose Response type includes HasCitations
    val webSearchTool = WebSearchTool(perplexityProvider, "sonar")
    
    given toolRegistry: ToolRegistry[IO] =
      ToolRegistry.empty[IO]
        .register(GetWeatherTool)
        .register(webSearchTool)

    // Create nodes - using OpenAI for main reasoning
    val chatNode = new ChatNode(openAIProvider, toolRegistry)
    val toolNode = new ToolNode

    // Build graph
    val graph = GraphBuilder[IO, AgentState]()
      .addNode(StartNode)
      .addNode(chatNode)
      .addNode(toolNode)
      .connect(StartNode).to(chatNode)
      .connect(chatNode).when(_.hasToolCalls).to(toolNode)
      .connect(chatNode).otherwise.toTerminal()
      .connect(toolNode).to(chatNode)
      .startFrom(StartNode)
      .build()

    println(
      s"Graph created with ${graph.nodes.size} nodes and ${graph.edges.size} edges"
    )
    println(s"Entry point: ${graph.entryPoint}")

    val executor = new GraphExecutor[IO]()
    val initialState = AgentState(
      messages = List(
        Message.User("What are the latest AI developments this week?")
      )
    )

    println("\n--- Executing Agent Graph ---")
    executor
      .run(graph, initialState)
      .compile
      .toList
      .flatMap { states =>
        IO {
          println(s"\nExecution completed with ${states.size} states:")
          states.zipWithIndex.foreach { case (state, idx) =>
            println(s"\n=== State $idx ===")
            state.messages.reverse.foreach {
              case Message.System(content) =>
                println(s"System: $content")
              case Message.User(content) =>
                println(s"User: $content")
              case Message.Assistant(AssistantContent.Text(value)) =>
                println(s"Assistant: $value")
              case Message.Assistant(AssistantContent.ToolCalls(calls)) =>
                println(s"Assistant: [Tool Calls]")
                calls.foreach { call =>
                  println(s"  - ${call.name}(${call.arguments.noSpaces})")
                }
              case Message.Tool(id, name, content) =>
                println(s"Tool ($name): $content")
            }
          }

          // Show final response
          println("\n=== Final Response ===")
          states.lastOption.flatMap(_.messages.headOption) match
            case Some(Message.Assistant(AssistantContent.Text(value))) =>
              println(value)
            case _ =>
              println("No final response")
        }
      }
  }

  result.unsafeRunSync()
