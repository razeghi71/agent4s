package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import cats.syntax.all.*
import no.marz.agent4s.llm.model.Tool
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description
import scala.sys.process.*
import scala.xml.XML
import java.io.File
import myfitnesspal.model.{UIElement, Bounds}

case class GetUIInput(
    @description("Optional device ID, if not provided uses default device")
    deviceId: Option[String] = None,
    @description("If true, returns raw XML instead of compact representation. Default is false (compact).")
    rawXml: Option[Boolean] = None
)

case class GetUIOutput(
    success: Boolean,
    @description("Compact UI representation with actionable elements only (or raw XML if rawXml=true)")
    uiContent: Option[String],
    @description("Number of actionable elements found")
    elementCount: Option[Int],
    errorMessage: Option[String]
)

object GetUIInput:
  given Decoder[GetUIInput] = deriveDecoder[GetUIInput]
  given Encoder[GetUIInput] = deriveEncoder[GetUIInput]

object GetUIOutput:
  given Encoder[GetUIOutput] = deriveEncoder[GetUIOutput]

/** Dump and retrieve UI hierarchy from Android device
  *
  * By default returns a compact representation with only actionable elements,
  * reducing token usage by ~86%. Use rawXml=true for full XML if needed.
  *
  * Compact format: [id] label (class) [actions] @ [bounds]
  * Example: [2] Log Food (View) [click] @ [228,1311][333,1416]
  */
class GetUITool[F[_]: Async] extends Tool[F, GetUIInput, GetUIOutput]:

  def name: String = "get_ui"

  def description: String =
    "Get the current UI state from Android device. Returns compact representation of actionable elements (buttons, inputs, text). Use element IDs to interact with tap/click tools."

  def execute(input: GetUIInput): F[GetUIOutput] =
    (for {
      // Step 1: Dump UI to device
      _ <- AdbBase.executeShell("uiautomator dump", input.deviceId)
      
      // Step 2: Pull the dumped file
      xmlContent <- pullUIDump(input.deviceId)
      
      // Step 3: Parse and convert to compact format (unless rawXml requested)
      result <- if input.rawXml.getOrElse(false) then
        Async[F].pure((xmlContent, None))
      else
        parseToCompact(xmlContent)
      
    } yield GetUIOutput(
      success = true, 
      uiContent = Some(result._1), 
      elementCount = result._2,
      errorMessage = None
    )).handleErrorWith { error =>
        Async[F].pure(
          GetUIOutput(
            success = false,
            uiContent = None,
            elementCount = None,
            errorMessage = Some(error.getMessage)
          )
        )
      }

  private def pullUIDump(deviceId: Option[String]): F[String] =
    Async[F].blocking {
      val deviceFlag = deviceId.map(id => s"-s $id ").getOrElse("")
      val tempFile = s"/tmp/ui_dump_${System.currentTimeMillis()}.xml"

      // Pull file from device
      val pullCmd = s"adb ${deviceFlag}pull /sdcard/window_dump.xml $tempFile"
      Process(pullCmd).!!

      // Read file content
      val content = scala.io.Source.fromFile(tempFile).mkString
      new File(tempFile).delete() // Cleanup

      content
    }

  /** Parse XML and return compact representation */
  private def parseToCompact(xmlContent: String): F[(String, Option[Int])] =
    Async[F].delay {
      val xml = XML.loadString(xmlContent)
      val root = parseNode((xml \\ "node").head, "0")
      val compact = UIElement.toCompactText(root)
      val count = UIElement.findActionable(root).size
      (compact, Some(count))
    }

  /** Parse XML node into UIElement */
  private def parseNode(node: scala.xml.Node, indexPath: String): UIElement =
    val className = (node \@ "class")
    val text = Option(node \@ "text").filter(_.nonEmpty)
    val contentDesc = Option(node \@ "content-desc").filter(_.nonEmpty)
    val resourceId = Option(node \@ "resource-id").filter(_.nonEmpty)
    val boundsStr = node \@ "bounds"
    val bounds = Bounds.parse(boundsStr).getOrElse(Bounds(0, 0, 0, 0))
    val clickable = (node \@ "clickable") == "true"
    val scrollable = (node \@ "scrollable") == "true"
    val focusable = (node \@ "focusable") == "true"
    val enabled = (node \@ "enabled") == "true"

    val childNodes = node \ "node"
    val children = childNodes.zipWithIndex.map { case (childNode, idx) =>
      parseNode(childNode, s"$indexPath.$idx")
    }.toList

    UIElement(
      index = indexPath,
      className = className,
      text = text,
      contentDesc = contentDesc,
      resourceId = resourceId,
      bounds = bounds,
      clickable = clickable,
      scrollable = scrollable,
      focusable = focusable,
      enabled = enabled,
      children = children
    )
