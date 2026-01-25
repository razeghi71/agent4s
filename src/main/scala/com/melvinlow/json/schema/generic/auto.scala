package com.melvinlow.json.schema.generic

import scala.annotation.nowarn
import scala.compiletime.{constValue, erasedValue, error, summonInline}
import scala.deriving.Mirror

import io.circe.Json

import com.melvinlow.json.schema.annotation.JsonSchemaField
import com.melvinlow.json.schema.{JsonSchemaEncoder, instances}

// Holds both the encoder and whether the field is optional
case class FieldInfo(encoder: JsonSchemaEncoder[?], isOptional: Boolean)

trait auto extends instances:

  inline private def summonLabels[Elems <: Tuple]: List[String] =
    inline erasedValue[Elems] match
      case _: (elem *: elems) =>
        constValue[elem].toString :: summonLabels[elems]
      case _: EmptyTuple => Nil

  inline private def summonFieldInfo[T, Elems <: Tuple]: List[FieldInfo] =
    inline erasedValue[Elems] match
      case _: (elem *: elems) =>
        val isOptional = inline erasedValue[elem] match
          case _: Option[?] => true
          case _            => false
        FieldInfo(deriveOrSummon[T, elem], isOptional) :: summonFieldInfo[
          T,
          elems
        ]
      case _: EmptyTuple => Nil

  @nowarn
  inline private def deriveOrSummon[T, Elem]: JsonSchemaEncoder[Elem] =
    inline erasedValue[Elem] match
      case _: Option[?] =>
        summonInline[JsonSchemaEncoder[
          Elem
        ]] // Always summon for Option, never derive
      case _: T => deriveRec[T, Elem]
      case _    => summonInline[JsonSchemaEncoder[Elem]]

  @nowarn
  inline private def deriveRec[T, Elem]: JsonSchemaEncoder[Elem] =
    inline erasedValue[T] match
      case _: Elem =>
        error("infinite recursive derivation")
      case _ =>
        derived[Elem](using summonInline[Mirror.Of[Elem]])

  private def sumEncoder[T: Mirror.SumOf](
      elems: => List[JsonSchemaEncoder[?]],
      elemLabels: => List[String],
      childAnnotations: => Map[String, List[(String, Json)]],
      typeAnnotations: => List[(String, Json)]
  ): JsonSchemaEncoder[T] = new JsonSchemaEncoder[T]:
    override def schema: Json =
      // Check if all elements are simple (parameterless enum cases)
      val isSimpleEnum = elems.forall { elem =>
        val elemSchema = elem.schema
        // A simple enum case has type "object" with empty or no properties
        elemSchema.hcursor.downField("type").as[String].contains("object") &&
        elemSchema.hcursor
          .downField("properties")
          .as[io.circe.JsonObject]
          .map(_.isEmpty)
          .getOrElse(true)
      }

      if isSimpleEnum then
        // Generate string enum schema
        Json
          .obj(
            "type" -> Json.fromString("string"),
            "enum" -> Json.arr(elemLabels.map(Json.fromString)*)
          )
          .deepMerge(Json.obj(typeAnnotations*))
      else
        // Keep existing anyOf behavior for complex enums
        Json
          .obj(
            "anyOf" -> Json.arr(
              elems.zip(elemLabels).map { (elem, label) =>
                val annotations = childAnnotations.getOrElse(label, Nil)
                elem.schema.deepMerge(Json.obj(annotations*))
              }*
            )
          )
          .deepMerge(Json.obj(typeAnnotations*))

  private def productEncoder[T: Mirror.ProductOf](
      fieldInfos: => List[FieldInfo],
      elemLabels: => List[String],
      constructorAnnotations: => Map[String, List[(String, Json)]],
      typeAnnotations: => List[(String, Json)]
  ): JsonSchemaEncoder[T] =
    new JsonSchemaEncoder[T]:
      override def schema =
        // Build properties
        val properties = fieldInfos.zip(elemLabels).map { case (info, label) =>
          val annotations = constructorAnnotations.getOrElse(label, Nil)
          label -> info.encoder.schema.deepMerge(Json.obj(annotations*))
        }

        // Build required list (fields that are NOT optional)
        val required = fieldInfos.zip(elemLabels).collect {
          case (info, label) if !info.isOptional => label
        }

        // Build base schema with properties and additionalProperties = false
        val baseSchema = Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj(properties*),
          "additionalProperties" -> Json.fromBoolean(false)
        )

        // Add required array if there are required fields
        val withRequired =
          if required.nonEmpty then
            baseSchema.deepMerge(
              Json.obj("required" -> Json.arr(required.map(Json.fromString)*))
            )
          else baseSchema

        // Merge type-level annotations
        withRequired.deepMerge(Json.obj(typeAnnotations*))

  // Keep the old summonInstances for sum types (they don't need optionality info)
  inline private def summonInstances[T, Elems <: Tuple]
      : List[JsonSchemaEncoder[?]] =
    inline erasedValue[Elems] match
      case _: (elem *: elems) =>
        deriveOrSummon[T, elem] :: summonInstances[T, elems]
      case _: EmptyTuple => Nil

  inline def derived[T](using m: Mirror.Of[T]): JsonSchemaEncoder[T] =
    // Special case: if T is Option or List, summon the explicit instance instead of deriving
    inline erasedValue[T] match
      case _: Option[?] => summonInline[JsonSchemaEncoder[T]]
      case _: List[?]   => summonInline[JsonSchemaEncoder[T]]
      case _            =>
        lazy val elemLabels = summonLabels[m.MirroredElemLabels]

        inline m match
          case s: Mirror.SumOf[T] =>
            // For sum types, use regular summonInstances (no optionality tracking needed)
            val elemInstances = summonInstances[T, m.MirroredElemTypes]
            sumEncoder(
              elemInstances,
              elemLabels,
              childAnnotations = JsonSchemaField.onChildrenOf[T],
              typeAnnotations = JsonSchemaField.onType[T]
            )(using s)

          case p: Mirror.ProductOf[T] =>
            // For product types, use summonFieldInfo to track optionality
            val fieldInfos = summonFieldInfo[T, m.MirroredElemTypes]
            productEncoder(
              fieldInfos,
              elemLabels,
              constructorAnnotations = JsonSchemaField.onConstructorParamsOf[T],
              typeAnnotations = JsonSchemaField.onType[T]
            )(using p)

  inline given derivedProduct[T: Mirror.ProductOf]: JsonSchemaEncoder[T] =
    derived[T]

  inline given derivedSum[T: Mirror.SumOf]: JsonSchemaEncoder[T] =
    derived[T]

object auto extends auto with semiauto
