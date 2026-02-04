package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import cats.syntax.all.*
import no.marz.agent4s.llm.model.Tool
import io.circe.generic.auto.given
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description

case class KillAppInput(
    @description(
      "Package name of the app to kill (e.g., com.myfitnesspal.android)"
    )
    packageName: String,
    @description("Optional device ID, if not provided uses default device")
    deviceId: Option[String] = None
)

case class KillAppOutput(success: Boolean, message: String)

/** Force stop an Android app
  *
  * Use this to kill an app completely and start fresh. Useful for ensuring
  * clean state before automation.
  */
class KillAppTool[F[_]: Async] extends Tool[F, KillAppInput, KillAppOutput]:

  def name: String = "kill_app"

  def description: String =
    "Force stop an Android app to ensure clean state"

  def execute(input: KillAppInput): F[KillAppOutput] =
    AdbBase
      .executeShell(s"am force-stop ${input.packageName}", input.deviceId)
      .map { _ =>
        KillAppOutput(success = true, message = s"Killed ${input.packageName}")
      }
      .handleErrorWith { error =>
        Async[F].pure(
          KillAppOutput(
            success = false,
            message = s"Kill failed: ${error.getMessage}"
          )
        )
      }
