package no.marz.agent4s.graph

/** A graph definition containing nodes and edges.
  *
  * Graphs are immutable and constructed using GraphBuilder.
  *
  * Example:
  * {{{
  * val graph = GraphBuilder[IO, MyState]()
  *   .addNode(StartNode)
  *   .addNode(ChatNode)
  *   .connect(StartNode).to(ChatNode)
  *   .startFrom(StartNode)
  *   .build()
  * }}}
  *
  * @tparam F
  *   The effect type
  * @tparam State
  *   The state type
  * @param nodes
  *   All nodes in the graph
  * @param edges
  *   All edges (connections) in the graph
  * @param entryPoint
  *   The starting node
  */
class Graph[F[_], State <: GraphState](
    val nodes: List[GraphNode[F, State]],
    val edges: List[Edge[F, State]],
    val entryPoint: GraphNode[F, State]
)
