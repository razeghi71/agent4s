package no.marz.agent4s.graph

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import munit.FunSuite

class GraphExecutorSuite extends FunSuite:

  // Test state
  case class TestState(value: Int, visited: List[String] = List.empty)
      extends GraphState

  // Test nodes
  object NodeA extends GraphNode[IO, TestState]:
    def execute(state: TestState): IO[TestState] =
      IO.pure(state.copy(
        value = state.value + 1,
        visited = "A" :: state.visited
      ))

  object NodeB extends GraphNode[IO, TestState]:
    def execute(state: TestState): IO[TestState] =
      IO.pure(state.copy(
        value = state.value + 10,
        visited = "B" :: state.visited
      ))

  object NodeC extends GraphNode[IO, TestState]:
    def execute(state: TestState): IO[TestState] =
      IO.pure(state.copy(
        value = state.value + 100,
        visited = "C" :: state.visited
      ))

  object NodeD extends GraphNode[IO, TestState]:
    def execute(state: TestState): IO[TestState] =
      IO.pure(state.copy(
        value = state.value + 1000,
        visited = "D" :: state.visited
      ))

  val executor = new GraphExecutor[IO]()

  test("execute simple linear graph: A -> B -> Terminal") {
    val graph = GraphBuilder[IO, TestState]()
      .addNode(NodeA)
      .addNode(NodeB)
      .connect(NodeA).to(NodeB)
      .connect(NodeB).otherwise.toTerminal()
      .startFrom(NodeA)
      .build()

    val initialState = TestState(0)
    val states =
      executor.run(graph, initialState).compile.toList.unsafeRunSync()

    assertEquals(states.size, 3) // A, B, Terminal
    assertEquals(states(0).value, 1) // After A
    assertEquals(states(0).visited, List("A"))
    assertEquals(states(1).value, 11) // After B
    assertEquals(states(1).visited, List("B", "A"))
    assertEquals(states(2).value, 11) // At terminal (unchanged)
    assertEquals(states(2).visited, List("B", "A"))
  }

  test("execute conditional branching: A -> (B or C) -> Terminal") {
    // If value < 5, go to B; otherwise go to C
    val graph = GraphBuilder[IO, TestState]()
      .addNode(NodeA)
      .addNode(NodeB)
      .addNode(NodeC)
      .connect(NodeA).when(_.value < 5).to(NodeB)
      .connect(NodeA).otherwise.to(NodeC)
      .connect(NodeB).otherwise.toTerminal()
      .connect(NodeC).otherwise.toTerminal()
      .startFrom(NodeA)
      .build()

    // Test path A -> B (value starts at 0, becomes 1)
    val states1 = executor
      .run(graph, TestState(0))
      .compile
      .toList
      .unsafeRunSync()

    assertEquals(states1.size, 3) // A, B, Terminal
    assertEquals(states1(0).visited, List("A"))
    assertEquals(states1(1).visited, List("B", "A"))

    // Test path A -> C (value starts at 10, stays >= 5)
    val states2 = executor
      .run(graph, TestState(10))
      .compile
      .toList
      .unsafeRunSync()

    assertEquals(states2.size, 3) // A, C, Terminal
    assertEquals(states2(0).visited, List("A"))
    assertEquals(states2(1).visited, List("C", "A"))
  }

  test("execute graph with loop: A -> B -> A (limited iterations)") {
    // Loop back from B to A while value < 3
    val graph = GraphBuilder[IO, TestState]()
      .addNode(NodeA)
      .addNode(NodeB)
      .connect(NodeA).to(NodeB)
      .connect(NodeB).when(_.value < 3).to(NodeA)
      .connect(NodeB).otherwise.toTerminal()
      .startFrom(NodeA)
      .build()

    val states = executor
      .run(graph, TestState(0))
      .compile
      .toList
      .unsafeRunSync()

    // Flow: A(0->1) -> B(1->11) -> Terminal (11 >= 3)
    assertEquals(states.size, 3)
    assertEquals(states(0).value, 1) // After first A
    assertEquals(states(1).value, 11) // After first B
    assertEquals(states(2).value, 11) // Terminal
  }

  test("execute graph with multiple conditionals (first match wins)") {
    val graph = GraphBuilder[IO, TestState]()
      .addNode(NodeA)
      .addNode(NodeB)
      .addNode(NodeC)
      .addNode(NodeD)
      .connect(NodeA).when(_.value > 0).to(NodeB) // First condition
      .connect(NodeA).when(_.value > 5).to(NodeC) // Second condition
      .connect(NodeA).otherwise.to(NodeD)
      .connect(NodeB).otherwise.toTerminal()
      .connect(NodeC).otherwise.toTerminal()
      .connect(NodeD).otherwise.toTerminal()
      .startFrom(NodeA)
      .build()

    // value = 10, matches both conditions but should take first (B)
    val states = executor
      .run(graph, TestState(10))
      .compile
      .toList
      .unsafeRunSync()

    assertEquals(states.size, 3) // A, B, Terminal
    assertEquals(states(1).visited, List("B", "A"))
  }

  test("error when no edge matches") {
    val graph = GraphBuilder[IO, TestState]()
      .addNode(NodeA)
      .addNode(NodeB)
      .connect(NodeA).when(_.value > 100).to(NodeB) // Condition never matches
      .connect(NodeB).otherwise.toTerminal()
      .startFrom(NodeA)
      .build()

    val error = intercept[IllegalStateException] {
      executor.run(graph, TestState(0)).compile.toList.unsafeRunSync()
    }

    assert(error.getMessage.contains("No edge matched"))
  }

  test("emit intermediate states correctly") {
    val graph = GraphBuilder[IO, TestState]()
      .addNode(NodeA)
      .addNode(NodeB)
      .addNode(NodeC)
      .connect(NodeA).to(NodeB)
      .connect(NodeB).to(NodeC)
      .connect(NodeC).otherwise.toTerminal()
      .startFrom(NodeA)
      .build()

    val states = executor
      .run(graph, TestState(0))
      .compile
      .toList
      .unsafeRunSync()

    // Should emit: after A, after B, after C, at terminal (4 states total)
    assertEquals(states.size, 4)
    assertEquals(states(0).value, 1) // A adds 1
    assertEquals(states(1).value, 11) // B adds 10
    assertEquals(states(2).value, 111) // C adds 100
    assertEquals(states(3).value, 111) // Terminal (no change)
  }

  test("direct to terminal without intermediate nodes") {
    val graph = GraphBuilder[IO, TestState]()
      .addNode(NodeA)
      .connect(NodeA).otherwise.toTerminal()
      .startFrom(NodeA)
      .build()

    val states = executor
      .run(graph, TestState(0))
      .compile
      .toList
      .unsafeRunSync()

    assertEquals(states.size, 2) // A, Terminal
    assertEquals(states(0).value, 1)
    assertEquals(states(1).value, 1)
  }
