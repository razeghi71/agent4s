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
import java.io.File

case class GetUIInput(
    @description("Optional device ID, if not provided uses default device")
    deviceId: Option[String] = None
)

case class GetUIOutput(
    success: Boolean,
    xmlContent: Option[String],
    errorMessage: Option[String]
)

object GetUIInput:
  given Decoder[GetUIInput] = deriveDecoder[GetUIInput]
  given Encoder[GetUIInput] = deriveEncoder[GetUIInput]

object GetUIOutput:
  given Encoder[GetUIOutput] = deriveEncoder[GetUIOutput]

/** Dump and retrieve UI hierarchy as XML
  *
  * This combines DumpUI and PullUIDump into one operation.
  * Returns the XML structure of the current screen.
  */
class GetUITool[F[_]: Async] extends Tool[F, GetUIInput, GetUIOutput]:

  def name: String = "get_ui_hierarchy"

  def description: String =
    "Dump and retrieve the current UI hierarchy as XML from Android device"

  def execute(input: GetUIInput): F[GetUIOutput] =
    (for {
      // Step 1: Dump UI to device
      _ <- AdbBase.executeShell("uiautomator dump", input.deviceId)
      
      // Step 2: Pull the dumped file
      xml <- pullUIDump(input.deviceId)
      
    } yield GetUIOutput(success = true, xmlContent = Some(xml), errorMessage = None))
      .handleErrorWith { error =>
        Async[F].pure(
          GetUIOutput(
            success = false,
            xmlContent = None,
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
