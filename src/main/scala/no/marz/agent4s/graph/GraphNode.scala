package no.marz.agent4s.graph

/** A node in the graph that executes logic and transforms state.
  *
  * Users implement this trait to define their execution logic.
  *
  * @tparam F
  *   The effect type (e.g., IO, Future)
  * @tparam State
  *   The state type that flows through the graph
  */
trait GraphNode[F[_], State <: GraphState]:
  /** Execute this node's logic, transforming the input state.
    *
    * @param state
    *   The current state
    * @return
    *   The transformed state wrapped in effect F
    */
  def execute(state: State): F[State]
