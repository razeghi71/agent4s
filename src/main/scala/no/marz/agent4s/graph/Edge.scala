package no.marz.agent4s.graph

/** Internal representation of edges between nodes.
  *
  * Edges are not exposed in the public API. Users create edges through the
  * GraphBuilder fluent API.
  *
  * Two types of edges:
  *   - ConditionalEdge: taken when condition is true
  *   - OtherwiseEdge: catch-all, taken when no conditional edges match
  *
  * Edge evaluation follows "first match wins" semantics:
  *   1. Conditional edges are evaluated in registration order
  *   2. First conditional edge with true condition is taken
  *   3. If no conditional edge matches, otherwise edge is taken
  *   4. If no edge matches at all, execution fails
  *
  * @tparam F
  *   The effect type
  * @tparam State
  *   The state type
  */
private[graph] sealed trait Edge[F[_], State <: GraphState]:
  def from: GraphNode[F, State]
  def to: GraphNode[F, State]

/** An edge with a condition that must be true to traverse.
  *
  * Multiple conditional edges from the same node are allowed. They are
  * evaluated in registration order (first match wins).
  *
  * @param from
  *   The source node
  * @param to
  *   The destination node
  * @param condition
  *   The condition that must be true to traverse this edge
  */
private[graph] case class ConditionalEdge[F[_], State <: GraphState](
    from: GraphNode[F, State],
    to: GraphNode[F, State],
    condition: State => Boolean
) extends Edge[F, State]

/** A catch-all edge taken when no conditional edges match.
  *
  * Only one otherwise edge is allowed per source node. This is validated at
  * graph build time.
  *
  * @param from
  *   The source node
  * @param to
  *   The destination node
  */
private[graph] case class OtherwiseEdge[F[_], State <: GraphState](
    from: GraphNode[F, State],
    to: GraphNode[F, State]
) extends Edge[F, State]
