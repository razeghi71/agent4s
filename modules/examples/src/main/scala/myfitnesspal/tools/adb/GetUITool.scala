package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import cats.syntax.all.*
import no.marz.agent4s.llm.model.Tool
import io.circe.generic.auto.given
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description
import scala.sys.process.*
import scala.xml.XML
import scala.util.Using
import java.io.File
import myfitnesspal.model.UIElement

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
      val tempFile = new File(s"/tmp/ui_dump_${System.currentTimeMillis()}.xml")

      try
        val pullCmd = s"adb ${deviceFlag}pull /sdcard/window_dump.xml ${tempFile.getAbsolutePath}"
        Process(pullCmd).!!
        Using.resource(scala.io.Source.fromFile(tempFile))(_.mkString)
      finally
        tempFile.delete()
    }

  /** Parse XML and return compact representation */
  private def parseToCompact(xmlContent: String): F[(String, Option[Int])] =
    Async[F].delay {
      val xml = XML.loadString(xmlContent)
      val root = UIElement.fromXmlNode((xml \\ "node").head, "0")
      val compact = UIElement.toCompactText(root)
      val count = UIElement.findActionable(root).size
      (compact, Some(count))
    }
