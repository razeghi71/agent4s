package no.marz.agent4s.llm

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.syntax.*
import com.melvinlow.json.schema.annotation.description
import com.melvinlow.json.schema.generic.auto.given
import io.circe.generic.auto.given
import no.marz.agent4s.llm.model.*
import no.marz.agent4s.llm.ToolRegistry.execute

class ToolRegistrySuite extends munit.FunSuite:

  // Test fixtures
  case class AddInput(a: Int, b: Int)
  case class AddOutput(result: Int)

  object AddTool extends Tool[IO, AddInput, AddOutput]:
    def name: String = "add"
    def description: String = "Adds two numbers"
    def execute(input: AddInput): IO[AddOutput] =
      IO.pure(AddOutput(input.a + input.b))

  case class MultiplyInput(x: Int, y: Int)
  case class MultiplyOutput(product: Int)

  object MultiplyTool extends Tool[IO, MultiplyInput, MultiplyOutput]:
    def name: String = "multiply"
    def description: String = "Multiplies two numbers"
    def execute(input: MultiplyInput): IO[MultiplyOutput] =
      IO.pure(MultiplyOutput(input.x * input.y))

  object FailingTool extends Tool[IO, AddInput, AddOutput]:
    def name: String = "failing"
    def description: String = "Always fails"
    def execute(input: AddInput): IO[AddOutput] =
      IO.raiseError(new RuntimeException("Intentional failure"))

  // Tests

  test("Empty registry has no schemas") {
    val registry = ToolRegistry.empty[IO]
    assertEquals(registry.getSchemas.size, 0)
  }

  test("Register single tool successfully") {
    val registry = ToolRegistry.empty[IO]
      .register(AddTool)

    val schemas = registry.getSchemas
    assertEquals(schemas.size, 1)

    val schema = schemas.head
    assertEquals(schema.name, "add")
    assertEquals(schema.description, "Adds two numbers")
  }

  test("Register multiple tools successfully") {
    val registry = ToolRegistry.empty[IO]
      .register(AddTool)
      .register(MultiplyTool)

    val schemas = registry.getSchemas
    assertEquals(schemas.size, 2)

    val names = schemas.map(_.name)
    assert(names.contains("add"))
    assert(names.contains("multiply"))
  }

  test("Reject duplicate tool names") {
    val registry = ToolRegistry.empty[IO]
      .register(AddTool)

    interceptMessage[IllegalArgumentException](
      "Tool 'add' is already registered"
    ) {
      registry.register(AddTool)
    }
  }

  test("Execute tool successfully") {
    val registry = ToolRegistry.empty[IO]
      .register(AddTool)

    val toolCall = ToolCall(
      id = "call_123",
      name = "add",
      arguments = Json.obj("a" -> Json.fromInt(5), "b" -> Json.fromInt(3))
    )

    val result = registry.execute(toolCall).unsafeRunSync()

    assertEquals(result.toolCallId, "call_123")
    assertEquals(result.toolName, "add")

    val content = io.circe.parser.parse(result.content).toOption.get
    assertEquals(content.hcursor.get[Int]("result").toOption, Some(8))
  }

  test("Execute tool with extension method") {
    given ToolRegistry[IO] = ToolRegistry.empty[IO].register(AddTool)

    val toolCall = ToolCall(
      id = "call_456",
      name = "add",
      arguments = Json.obj("a" -> Json.fromInt(10), "b" -> Json.fromInt(20))
    )

    val result = toolCall.execute[IO]().unsafeRunSync()

    assertEquals(result.toolCallId, "call_456")
    assertEquals(result.toolName, "add")

    val content = io.circe.parser.parse(result.content).toOption.get
    assertEquals(content.hcursor.get[Int]("result").toOption, Some(30))
  }

  test("Tool not found error (returnErrorsAsToolMessages = true)") {
    val registry = ToolRegistry.empty[IO]
      .register(AddTool)

    val toolCall = ToolCall(
      id = "call_789",
      name = "nonexistent",
      arguments = Json.obj()
    )

    val result = registry.execute(
      toolCall,
      returnErrorsAsToolMessages = true
    ).unsafeRunSync()

    assertEquals(result.toolCallId, "call_789")
    assertEquals(result.toolName, "nonexistent")

    val content = io.circe.parser.parse(result.content).toOption.get
    val error = content.hcursor.get[String]("error").toOption.get
    assert(error.contains("Tool not found: 'nonexistent'"))
    assertEquals(
      content.hcursor.get[String]("type").toOption,
      Some("RuntimeException")
    )
  }

  test("Tool not found error (returnErrorsAsToolMessages = false)") {
    val registry = ToolRegistry.empty[IO]
      .register(AddTool)

    val toolCall = ToolCall(
      id = "call_999",
      name = "nonexistent",
      arguments = Json.obj()
    )

    interceptMessage[RuntimeException]("Tool not found: 'nonexistent'") {
      registry.execute(
        toolCall,
        returnErrorsAsToolMessages = false
      ).unsafeRunSync()
    }
  }

  test("JSON decode failure (returnErrorsAsToolMessages = true)") {
    val registry = ToolRegistry.empty[IO]
      .register(AddTool)

    val toolCall = ToolCall(
      id = "call_decode",
      name = "add",
      arguments = Json.obj("invalid" -> Json.fromString("not an int"))
    )

    val result = registry.execute(
      toolCall,
      returnErrorsAsToolMessages = true
    ).unsafeRunSync()

    assertEquals(result.toolCallId, "call_decode")
    assertEquals(result.toolName, "add")

    val content = io.circe.parser.parse(result.content).toOption.get
    val error = content.hcursor.get[String]("error").toOption.get
    assert(error.contains("Failed to decode tool arguments for 'add'"))
    assertEquals(
      content.hcursor.get[String]("type").toOption,
      Some("RuntimeException")
    )
  }

  test("JSON decode failure (returnErrorsAsToolMessages = false)") {
    val registry = ToolRegistry.empty[IO]
      .register(AddTool)

    val toolCall = ToolCall(
      id = "call_decode2",
      name = "add",
      arguments = Json.obj("wrong" -> Json.fromString("bad"))
    )

    val thrown = intercept[RuntimeException] {
      registry.execute(
        toolCall,
        returnErrorsAsToolMessages = false
      ).unsafeRunSync()
    }

    assert(
      thrown.getMessage.contains("Failed to decode tool arguments for 'add'")
    )
  }

  test("Tool execution failure (returnErrorsAsToolMessages = true)") {
    val registry = ToolRegistry.empty[IO]
      .register(FailingTool)

    val toolCall = ToolCall(
      id = "call_fail",
      name = "failing",
      arguments = Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromInt(2))
    )

    val result = registry.execute(
      toolCall,
      returnErrorsAsToolMessages = true
    ).unsafeRunSync()

    assertEquals(result.toolCallId, "call_fail")
    assertEquals(result.toolName, "failing")

    val content = io.circe.parser.parse(result.content).toOption.get
    val error = content.hcursor.get[String]("error").toOption.get
    assertEquals(error, "Intentional failure")
    assertEquals(
      content.hcursor.get[String]("type").toOption,
      Some("RuntimeException")
    )
  }

  test("Tool execution failure (returnErrorsAsToolMessages = false)") {
    val registry = ToolRegistry.empty[IO]
      .register(FailingTool)

    val toolCall = ToolCall(
      id = "call_fail2",
      name = "failing",
      arguments = Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromInt(2))
    )

    interceptMessage[RuntimeException]("Intentional failure") {
      registry.execute(
        toolCall,
        returnErrorsAsToolMessages = false
      ).unsafeRunSync()
    }
  }

  test("getSchemas returns all registered tool schemas") {
    val registry = ToolRegistry.empty[IO]
      .register(AddTool)
      .register(MultiplyTool)

    val schemas = registry.getSchemas
    assertEquals(schemas.size, 2)

    val schemaMap = schemas.map(s => s.name -> s).toMap

    assert(schemaMap.contains("add"))
    assertEquals(schemaMap("add").description, "Adds two numbers")

    assert(schemaMap.contains("multiply"))
    assertEquals(schemaMap("multiply").description, "Multiplies two numbers")

    // Each schema should have valid parameters
    schemaMap.values.foreach { schema =>
      assert(schema.parameters.isObject)
    }
  }

  test("Registry is immutable - register returns new instance") {
    val registry1 = ToolRegistry.empty[IO]
    val registry2 = registry1.register(AddTool)
    val registry3 = registry2.register(MultiplyTool)

    assertEquals(registry1.getSchemas.size, 0)
    assertEquals(registry2.getSchemas.size, 1)
    assertEquals(registry3.getSchemas.size, 2)
  }
