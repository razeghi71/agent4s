package no.marz.agent4s.graph

import cats.Monad

/** Fluent API builder for constructing graphs.
  *
  * Users build graphs by adding nodes and connecting them with conditions.
  *
  * Example:
  * {{{
  * val graph = GraphBuilder[IO, MyState]()
  *   .addNode(StartNode)
  *   .addNode(ChatNode)
  *   .addNode(ToolNode)
  *   .connect(StartNode).to(ChatNode)
  *   .connect(ChatNode).when(_.hasToolCalls).to(ToolNode)
  *   .connect(ChatNode).otherwise.to(EndNode)
  *   .startFrom(StartNode)
  *   .build()
  * }}}
  *
  * @tparam F
  *   The effect type
  * @tparam State
  *   The state type
  */
class GraphBuilder[F[_]: Monad, State <: GraphState] private (
    private val nodes: List[GraphNode[F, State]],
    private val edges: List[Edge[F, State]],
    private val entryPoint: Option[GraphNode[F, State]]
):

  /** Add a node to the graph.
    *
    * @param node
    *   The node to add
    * @return
    *   A new builder with the node added
    */
  def addNode(node: GraphNode[F, State]): GraphBuilder[F, State] =
    new GraphBuilder(node :: nodes, edges, entryPoint)

  /** Start connecting edges from a source node.
    *
    * @param from
    *   The source node
    * @return
    *   An EdgeBuilder for fluent edge construction
    */
  def connect(from: GraphNode[F, State]): EdgeBuilder[F, State] =
    new EdgeBuilder(this, from, None)

  /** Specify the entry point (starting node) of the graph.
    *
    * @param node
    *   The node to start execution from
    * @return
    *   A new builder with the entry point set
    */
  def startFrom(node: GraphNode[F, State]): GraphBuilder[F, State] =
    new GraphBuilder(nodes, edges, Some(node))

  /** Internal method to add an edge. Called by EdgeBuilder.
    */
  private[graph] def addEdge(
      edge: Edge[F, State]
  ): GraphBuilder[F, State] =
    new GraphBuilder(nodes, edge :: edges, entryPoint)

  /** Build the final Graph instance.
    *
    * Validates the graph structure:
    *   - Entry point must be set
    *   - All referenced nodes must be added
    *   - No multiple edges from the same node can be active simultaneously
    *
    * @return
    *   The constructed Graph
    * @throws IllegalStateException
    *   if validation fails
    */
  def build(): Graph[F, State] =
    // Validate entry point
    require(
      entryPoint.isDefined,
      "Graph must have an entry point. Use .startFrom(node)"
    )
    require(
      nodes.contains(entryPoint.get),
      "Entry point must be a registered node"
    )

    // Validate all edge nodes are registered
    edges.foreach { edge =>
      require(
        nodes.contains(edge.from),
        s"Edge source node not registered: ${edge.from}"
      )
      require(
        nodes.contains(edge.to),
        s"Edge target node not registered: ${edge.to}"
      )
    }

    // Group edges by source node for validation
    val edgesBySource = edges.groupBy(_.from)

    // Validate mutual exclusivity: each source node should have at most one "otherwise" edge
    edgesBySource.foreach { case (sourceNode, nodeEdges) =>
      val otherwiseCount = nodeEdges.count {
        case _: OtherwiseEdge[F, State] => true
        case _                          => false
      }
      require(
        otherwiseCount <= 1,
        s"Node $sourceNode has multiple 'otherwise' edges. Only one is allowed."
      )
    }

    new Graph[F, State](
      nodes.reverse, // Reverse to maintain insertion order
      edges.reverse,
      entryPoint.get
    )

object GraphBuilder:
  /** Create a new empty GraphBuilder.
    *
    * @tparam F
    *   The effect type
    * @tparam State
    *   The state type
    * @return
    *   A new empty GraphBuilder
    */
  def apply[F[_]: Monad, State <: GraphState](): GraphBuilder[F, State] =
    new GraphBuilder[F, State](List.empty, List.empty, None)

/** Intermediate builder for fluent edge construction.
  *
  * Created by GraphBuilder.connect(node). Allows specifying conditions and
  * targets.
  *
  * @tparam F
  *   The effect type
  * @tparam State
  *   The state type
  */
class EdgeBuilder[F[_], State <: GraphState] private[graph] (
    private val parent: GraphBuilder[F, State],
    private val from: GraphNode[F, State],
    private val condition: Option[State => Boolean]
):

  /** Add an unconditional edge to the target node.
    *
    * Use this when the source node always goes to the same next node.
    *
    * @param target
    *   The destination node
    * @return
    *   The parent GraphBuilder for continued chaining
    */
  def to(target: GraphNode[F, State]): GraphBuilder[F, State] =
    val cond = condition.getOrElse((_: State) => true)
    val edge = ConditionalEdge(from, target, cond)
    parent.addEdge(edge)

  /** Add a condition for edge traversal.
    *
    * The edge will only be taken if the condition evaluates to true.
    *
    * @param predicate
    *   The condition to check
    * @return
    *   A new EdgeBuilder with the condition set
    */
  def when(predicate: State => Boolean): EdgeBuilder[F, State] =
    new EdgeBuilder(parent, from, Some(predicate))

  /** Mark this edge as a catch-all "otherwise" edge.
    *
    * The otherwise edge is taken when no other conditional edges match. Only
    * one otherwise edge is allowed per source node.
    *
    * @return
    *   A new EdgeBuilder marked as otherwise
    */
  def otherwise: EdgeBuilder[F, State] =
    new EdgeBuilder(parent, from, None):
      override def to(target: GraphNode[F, State]): GraphBuilder[F, State] =
        val edge = OtherwiseEdge(from, target)
        parent.addEdge(edge)
