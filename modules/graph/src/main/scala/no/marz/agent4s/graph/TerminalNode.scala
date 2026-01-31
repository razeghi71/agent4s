package no.marz.agent4s.graph

import cats.Applicative

/** A special library-provided node that marks the end of graph execution.
  *
  * This node is automatically provided by the library and should not be
  * instantiated by users. Users connect to it via `.toTerminal()` method.
  *
  * When the executor reaches this node, execution stops and the final state is
  * returned.
  *
  * @tparam F
  *   The effect type
  * @tparam State
  *   The state type
  */
private[graph] class TerminalNode[F[_]: Applicative, State <: GraphState]
    extends GraphNode[F, State]:

  override def execute(state: State): F[State] =
    Applicative[F].pure(state)

  override def toString: String = "TerminalNode"

/** Factory for creating TerminalNode instances.
  *
  * Each graph gets its own TerminalNode instance to avoid type issues.
  */
object TerminalNode:
  def apply[F[_]: Applicative, State <: GraphState](): TerminalNode[F, State] =
    new TerminalNode[F, State]
