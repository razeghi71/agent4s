package no.marz.agent4s.llm.model

import io.circe.Json
import com.melvinlow.json.schema.annotation.description
import com.melvinlow.json.schema.generic.auto.given
import io.circe.generic.auto.given

class ToolSuite extends munit.FunSuite:
  test("Tool schema includes name, description, and input_schema") {
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

    object GetWeatherTool extends Tool[GetWeatherInput, GetWeatherOutput]:
      def name: String = "GetWeather"
      def description: String =
        "A Tool that given a location and unit returns the degree in that unit"
      def execute(input: GetWeatherInput): GetWeatherOutput =
        GetWeatherOutput(10, "C")

    val obtained = GetWeatherTool.schema
    val expected = Json.obj(
      "name" -> Json.fromString("GetWeather"),
      "description" -> Json.fromString(
        "A Tool that given a location and unit returns the degree in that unit"
      ),
      "input_schema" -> Json.obj(
        "type" -> Json.fromString("object"),
        "required" -> Json.arr(Json.fromString("location")),
        "properties" -> Json.obj(
          "location" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("The city and state, e.g. San Francisco, CA")
          ),
          "unit" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("centigrade or fahrenheit"),
            "enum" -> Json.arr(Json.fromString("C"), Json.fromString("F"))
          ),
          "address" -> Json.obj(
            "type" -> Json.fromString("object"),
            "description" -> Json.fromString("Optional address"),
            "required" -> Json.arr(Json.fromString("street"), Json.fromString("city")),
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
    )

    assertEquals(obtained, expected)
  }
