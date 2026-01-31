package no.marz.agent4s.llm.model

import cats.effect.IO
import io.circe.Json
import com.melvinlow.json.schema.annotation.description
import com.melvinlow.json.schema.generic.auto.given
import io.circe.generic.auto.given

class ToolDefinitionSuite extends munit.FunSuite:
  test("Tool schema includes name, description, and parameters") {
    enum WeatherUnit:
      case C, F

    case class Address(street: String, city: String)

    case class GetWeatherInput(
        @description("The city and state, e.g. San Francisco, CA")
        location: String,
        @description("centigrade or fahrenheit")
        unit: Option[WeatherUnit],
        @description("Optional address")
        address: Option[Address],
        @description("Optional tags")
        tags: Option[List[String]]
    )
    case class GetWeatherOutput(value: Float, unit: String)

    object GetWeatherTool extends Tool[IO, GetWeatherInput, GetWeatherOutput]:
      def name: String = "GetWeather"
      def description: String =
        "A Tool that given a location and unit returns the degree in that unit"
      def execute(input: GetWeatherInput): IO[GetWeatherOutput] =
        IO.pure(GetWeatherOutput(10, "C"))

    // Test inputSchema
    val obtainedInputSchema = GetWeatherTool.inputSchema
    val expectedInputSchema = Json.obj(
      "type" -> Json.fromString("object"),
      "required" -> Json.arr(Json.fromString("location")),
      "properties" -> Json.obj(
        "location" -> Json.obj(
          "type" -> Json.fromString("string"),
          "description" ->
            Json.fromString("The city and state, e.g. San Francisco, CA")
        ),
        "unit" -> Json.obj(
          "type" -> Json.fromString("string"),
          "description" -> Json.fromString("centigrade or fahrenheit"),
          "enum" -> Json.arr(Json.fromString("C"), Json.fromString("F"))
        ),
        "address" -> Json.obj(
          "type" -> Json.fromString("object"),
          "description" -> Json.fromString("Optional address"),
          "required" ->
            Json.arr(Json.fromString("street"), Json.fromString("city")),
          "properties" -> Json.obj(
            "street" -> Json.obj("type" -> Json.fromString("string")),
            "city" -> Json.obj("type" -> Json.fromString("string"))
          ),
          "additionalProperties" -> Json.fromBoolean(false)
        ),
        "tags" -> Json.obj(
          "type" -> Json.fromString("array"),
          "description" -> Json.fromString("Optional tags"),
          "items" -> Json.obj("type" -> Json.fromString("string"))
        )
      ),
      "additionalProperties" -> Json.fromBoolean(false)
    )

    assertEquals(obtainedInputSchema, expectedInputSchema)

    // Test toToolSchema
    val toolSchema = GetWeatherTool.toToolSchema
    assertEquals(toolSchema.name, "GetWeather")
    assertEquals(
      toolSchema.description,
      "A Tool that given a location and unit returns the degree in that unit"
    )
    assertEquals(toolSchema.parameters, expectedInputSchema)
  }

  test("ChatCompletionRequest rejects duplicate tool names") {
    val schema1 = ToolSchema(
      name = "get_weather",
      description = "Gets weather",
      parameters = Json.obj()
    )

    val schema2 = ToolSchema(
      name = "get_weather",
      description = "Different description",
      parameters = Json.obj("type" -> Json.fromString("object"))
    )

    // Should throw IllegalArgumentException for duplicate names
    interceptMessage[IllegalArgumentException](
      "requirement failed: Duplicate tool names found: get_weather"
    ) {
      ChatCompletionRequest(
        model = "gpt-4",
        messages = Seq(Message.User("test")),
        tools = Set(schema1, schema2)
      )
    }
  }

  test("ChatCompletionRequest allows tools with different names") {
    val schema1 = ToolSchema(
      name = "get_weather",
      description = "Gets weather",
      parameters = Json.obj()
    )

    val schema2 = ToolSchema(
      name = "calculator",
      description = "Calculator",
      parameters = Json.obj()
    )

    // Should succeed - different names
    val request = ChatCompletionRequest(
      model = "gpt-4",
      messages = Seq(Message.User("test")),
      tools = Set(schema1, schema2)
    )

    assertEquals(request.tools.size, 2)
  }
