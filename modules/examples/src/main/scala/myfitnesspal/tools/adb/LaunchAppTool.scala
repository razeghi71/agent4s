package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import cats.syntax.all.*
import no.marz.agent4s.llm.model.Tool
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description

case class LaunchAppInput(
    @description("Package name of the app to launch (e.g., com.myfitnesspal.android)")
    packageName: String,
    @description("Optional device ID, if not provided uses default device")
    deviceId: Option[String] = None
)

case class LaunchAppOutput(success: Boolean, message: String)

object LaunchAppInput:
  given Decoder[LaunchAppInput] = deriveDecoder[LaunchAppInput]
  given Encoder[LaunchAppInput] = deriveEncoder[LaunchAppInput]

object LaunchAppOutput:
  given Encoder[LaunchAppOutput] = deriveEncoder[LaunchAppOutput]

/** Launch an Android app by package name
  *
  * Uses monkey command which bypasses permission issues.
  */
class LaunchAppTool[F[_]: Async] extends Tool[F, LaunchAppInput, LaunchAppOutput]:

  def name: String = "launch_app"

  def description: String =
    "Launch an Android app by its package name"

  def execute(input: LaunchAppInput): F[LaunchAppOutput] =
    AdbBase
      .executeShell(
        s"monkey -p ${input.packageName} -c android.intent.category.LAUNCHER 1",
        input.deviceId
      )
      .map { output =>
        LaunchAppOutput(success = true, message = s"Launched ${input.packageName}")
      }
      .handleErrorWith { error =>
        Async[F].pure(
          LaunchAppOutput(success = false, message = s"Launch failed: ${error.getMessage}")
        )
      }
