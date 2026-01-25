package com.melvinlow.json.schema

import io.circe.Json

trait instances_low_priority {
  given intJsonSchemaInstance[T <: Int]: JsonSchemaEncoder[T] with {
    def schema: Json = Json.obj("type" -> Json.fromString("integer"))
  }

  given stringJsonSchemaInstance[T <: String]: JsonSchemaEncoder[T] with {
    def schema: Json = Json.obj("type" -> Json.fromString("string"))
  }

  given longJsonSchemaInstance[T <: Long]: JsonSchemaEncoder[T] with {
    def schema: Json = Json.obj("type" -> Json.fromString("integer"))
  }

  given doubleJsonSchemaInstance[T <: Double]: JsonSchemaEncoder[T] with {
    def schema: Json = Json.obj("type" -> Json.fromString("number"))
  }

  given floatJsonSchemaEncoder[T <: Float]: JsonSchemaEncoder[T] with {
    def schema: Json = Json.obj("type" -> Json.fromString("number"))
  }

  given booleanJsonSchemaEncoder[T <: Boolean]: JsonSchemaEncoder[T] with {
    def schema: Json = Json.obj("type" -> Json.fromString("boolean"))
  }

  given listJsonSchemaEncoder[T: JsonSchemaEncoder]: JsonSchemaEncoder[List[T]]
  with {
    def schema: Json =
      Json
        .obj(
          "type"  -> Json.fromString("array"),
          "items" -> JsonSchemaEncoder[T].schema
        )
  }

  given arrayJsonSchemaEncoder[T: JsonSchemaEncoder]
    : JsonSchemaEncoder[Array[T]]
  with {
    def schema: Json =
      Json
        .obj(
          "type"  -> Json.fromString("array"),
          "items" -> JsonSchemaEncoder[T].schema
        )
  }
}

trait instances extends instances_low_priority {
  given nullJsonSchemaEncoder: JsonSchemaEncoder[Null] with {
    def schema: Json = Json.obj("type" -> Json.fromString("null"))
  }
  given optionJsonSchemaEncoder[T: JsonSchemaEncoder]: JsonSchemaEncoder[Option[T]] with {
    def schema: Json =
      val innerSchema = JsonSchemaEncoder[T].schema
      // Extract the type from inner schema, or default to the inner schema structure
      val innerType = innerSchema.hcursor.downField("type").as[String].getOrElse("object")
      
      // Create union type: ["innerType", "null"]
      Json.obj(
        "type" -> Json.arr(
          Json.fromString(innerType),
          Json.fromString("null")
        )
      ).deepMerge(
        // Merge other properties from inner schema (like description, etc.) but remove the type
        innerSchema.hcursor.downField("type").delete.top.getOrElse(Json.obj())
      )
  }
}



object instances extends instances
