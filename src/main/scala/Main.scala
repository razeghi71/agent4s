import cats.effect.IO
import com.melvinlow.json.schema.generic.auto.given
import io.circe.generic.auto.given
import io.circe.{Decoder, Encoder}
import no.marz.agent4s.graph.*
import no.marz.agent4s.llm.model.{Tool, ToolCodec}
import com.melvinlow.json.schema.annotation.description

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
  override def execute(state: AgentState): IO[AgentState] = ???

object EndNode extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] = ???

object StartChat extends GraphEdge[IO, StartNode.type, ChatNode.type]
object ChatTool extends GraphEdge[IO, ChatNode.type, ToolNode.type]
object ChatEnd extends GraphEdge[IO, ChatNode.type, EndNode.type]
object ToolChat extends GraphEdge[IO, ToolNode.type, ChatNode.type]

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

object GetWeatherTool extends Tool[GetWeatherInput, GetWeatherOutput]:
  def name: String = "GetWeather"
  def description: String =
    "A Tool that given a location and unit returns the degree in that unit"
  def execute(input: GetWeatherInput): GetWeatherOutput =
    GetWeatherOutput(10, "C")

@main def hello(): Unit =
  val ValidGraph = new Graph[IO](
    nodes = StartNode :: ChatNode :: ToolNode :: EndNode :: GraphNodeNil[IO](),
    edges = StartChat +: ChatTool +: ChatEnd +: ToolChat +: GraphEdgeNil[IO]()
  )

  println(ValidGraph)
  println(GetWeatherTool.schema)
