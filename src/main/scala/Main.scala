import cats.effect.IO
import cats.effect.unsafe.implicits.global
import no.marz.agent4s.graph.*
import no.marz.agent4s.llm.model.{
  Tool,
  ToolMetadata,
  ToolSchema,
  ChatCompletionRequest,
  Message
}
import com.melvinlow.json.schema.annotation.description
import com.melvinlow.json.schema.generic.auto.given
import io.circe.generic.auto.given

case class AgentState(
    messages: List[String]
) extends GraphState

object StartNode extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] = IO { state }

object ChatNode extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] = IO {
    state.copy(
      messages = "hello" :: state.messages
    )
  }

object ToolNode extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] =
    IO.pure(state.copy(messages = "tool executed" :: state.messages))

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

// Now using Tool trait with execute method
object GetWeatherTool extends Tool[IO, GetWeatherInput, GetWeatherOutput]:
  def name: String = "GetWeather"
  def description: String =
    "A Tool that given a location and unit returns the degree in that unit"

  def execute(input: GetWeatherInput): IO[GetWeatherOutput] =
    IO.pure(GetWeatherOutput(22.5f, input.unit.fold("C")(_.toString)))

@main def hello(): Unit =
  val graph = GraphBuilder[IO, AgentState]()
    .addNode(StartNode)
    .addNode(ChatNode)
    .addNode(ToolNode)
    .connect(StartNode).to(ChatNode)
    .connect(ChatNode).when(_.messages.isEmpty).to(ToolNode)
    .connect(ChatNode).otherwise.toTerminal() // Use terminal node
    .connect(ToolNode).to(ChatNode)
    .startFrom(StartNode)
    .build()

  println(
    s"Graph created with ${graph.nodes.size} nodes and ${graph.edges.size} edges"
  )
  println(s"Entry point: ${graph.entryPoint}")

  // Execute the graph
  val executor = new GraphExecutor[IO]()
  val initialState = AgentState(List.empty)

  println("\n--- Executing Graph ---")
  val states = executor
    .run(graph, initialState)
    .compile
    .toList
    .unsafeRunSync()

  println(s"\nExecution completed with ${states.size} states:")
  states.zipWithIndex.foreach { case (state, idx) =>
    println(s"  State $idx: messages = ${state.messages.reverse}")
  }

  // Test tool execution
  val testInput = GetWeatherInput("Paris, FR", Some(WeatherUnit.C), None, None)
  val result = GetWeatherTool.execute(testInput).unsafeRunSync()
  println(s"\nTool execution result: $result")
