import cats.effect.IO
import no.marz.agent4s.graph.{
  ++:,
  +:,
  Graph,
  GraphEdge,
  GraphEdgeNil,
  GraphNode,
  GraphNodeNil,
  GraphState
}

case class AgentState(
    messages: List[String]
) extends GraphState

object StartNode extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] = ???

object ChatNode extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] = ???

object ToolNode extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] = ???

object EndNode extends GraphNode[IO, AgentState]:
  override def execute(state: AgentState): IO[AgentState] = ???
object StartChat extends GraphEdge[IO, StartNode.type, ChatNode.type]
object ChatTool extends GraphEdge[IO, ChatNode.type, ToolNode.type]
object ChatEnd extends GraphEdge[IO, ChatNode.type, EndNode.type]
object ToolChat extends GraphEdge[IO, ToolNode.type, ChatNode.type]

@main def hello(): Unit =
  val ValidGraph = new Graph[IO](
    nodes = StartNode +: ChatNode +: ToolNode +: EndNode +: GraphNodeNil[
      IO
    ](),
    edges =
      StartChat ++: ChatTool ++: ChatEnd ++: ToolChat ++: GraphEdgeNil[IO]()
  )

  println(ValidGraph)
