package no.marz.agent4s.graph

trait GraphEdge[F[_], N1 <: GraphNode[F, ?], N2 <: GraphNode[F, ?]]

sealed trait GraphEdgeList[F[_]]
case class GraphEdgeNil[F[_]]() extends GraphEdgeList[F]
case class GraphEdgeCons[F[_], H <: GraphEdge[F, ?, ?], T <: GraphEdgeList[F]](
    head: H,
    tail: T
) extends GraphEdgeList[F]

extension [F[_], H <: GraphEdge[F, ?, ?]](node: H)
  def ++:[T <: GraphEdgeList[F]](tail: T): GraphEdgeCons[F, H, T] =
    GraphEdgeCons(node, tail)
