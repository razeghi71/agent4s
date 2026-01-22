package no.marz.agent4s.graph

class Graph[F[_]](
    val nodes: GraphNodeList[F],
    val edges: GraphEdgeList[F]
)
