package no.marz.agent4s.graph

import cats.MonadError
import cats.implicits.*
import fs2.Stream

/** Executes a graph by traversing nodes according to edge conditions.
  *
  * Returns an fs2.Stream that emits state after each node execution, allowing
  * observation of the execution flow. The stream terminates when reaching a
  * TerminalNode.
  *
  * Edge evaluation semantics:
  *   - Conditional edges are evaluated in registration order
  *   - First edge with condition(state) == true is taken
  *   - If no conditional matches, OtherwiseEdge is taken
  *   - If no edge matches at all, execution fails with IllegalStateException
  *
  * @param F
  *   MonadError instance for effect type F
  */
class GraphExecutor[F[_]](using F: MonadError[F, Throwable]):

  /** Executes the graph starting from its entry point with initial state.
    *
    * @param graph
    *   The graph to execute
    * @param initialState
    *   The initial state to start execution with
    * @return
    *   Stream of states emitted after each node execution, including final
    *   state at terminal
    */
  def run[State <: GraphState](
      graph: Graph[F, State],
      initialState: State
  ): Stream[F, State] =
    // Build lookup map from source node to its outgoing edges
    val edgesBySource: Map[GraphNode[F, State], List[Edge[F, State]]] =
      graph.edges.groupBy {
        case e: ConditionalEdge[F, State] => e.from
        case e: OtherwiseEdge[F, State]   => e.from
      }

    // Main execution loop
    def loop(
        currentNode: GraphNode[F, State],
        state: State
    ): Stream[F, State] =
      // Check if we've reached terminal
      currentNode match
        case _: TerminalNode[F, State] =>
          // Terminal node - emit final state and stop
          Stream.emit(state)

        case _ =>
          // Execute current node and continue
          Stream.eval(currentNode.execute(state)).flatMap { newState =>
            // Find next node based on edges and state
            findNextNode(currentNode, newState, edgesBySource) match
              case Some(nextNode) =>
                // Emit intermediate state, then continue
                Stream.emit(newState) ++ loop(nextNode, newState)

              case None =>
                // No edge matched - this is an error
                Stream.raiseError[F](
                  new IllegalStateException(
                    s"No edge matched for node ${currentNode.getClass.getSimpleName}. " +
                      "Ensure you have either a matching conditional edge or an otherwise edge."
                  )
                )
          }

    // Start execution from entry point
    loop(graph.entryPoint, initialState)

  /** Finds the next node to execute based on edge conditions.
    *
    * Algorithm:
    *   1. Get all edges from current node
    *   2. Separate conditional from otherwise edges
    *   3. Try conditional edges in order - take first match
    *   4. If no conditional matches, take otherwise edge
    *   5. If no edges match, return None
    *
    * @param from
    *   Current node
    * @param state
    *   Current state
    * @param edgesBySource
    *   Lookup map of source node to edges
    * @return
    *   Next node to execute, or None if no edge matches
    */
  private def findNextNode[State <: GraphState](
      from: GraphNode[F, State],
      state: State,
      edgesBySource: Map[GraphNode[F, State], List[Edge[F, State]]]
  ): Option[GraphNode[F, State]] =
    edgesBySource.get(from) match
      case None =>
        // No outgoing edges - likely terminal reached
        None

      case Some(edges) =>
        // Try conditional edges first (in order)
        edges
          .collectFirst {
            case e: ConditionalEdge[?, ?]
                if e.asInstanceOf[ConditionalEdge[F, State]].condition(state) =>
              e.asInstanceOf[ConditionalEdge[F, State]].to
          }
          .orElse {
            // No conditional matched, try otherwise edge
            edges.collectFirst {
              case e: OtherwiseEdge[?, ?] =>
                e.asInstanceOf[OtherwiseEdge[F, State]].to
            }
          }
