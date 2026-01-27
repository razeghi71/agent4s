package no.marz.agent4s.graph

trait GraphNode[F[_], State <: GraphState]:
  def execute(state: State): F[State]

sealed trait GraphNodeList[F[_]]
case class GraphNodeNil[F[_]]() extends GraphNodeList[F]
case class GraphNodeCons[F[_], H <: GraphNode[F, ?], T <: GraphNodeList[F]](
    head: H,
    tail: T
) extends GraphNodeList[F]

extension [F[_], H <: GraphNode[F, ?]](node: H)
  def @:[T <: GraphNodeList[F]](tail: T): GraphNodeCons[F, H, T] =
    GraphNodeCons(node, tail)
