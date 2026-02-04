package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import cats.syntax.all.*
import no.marz.agent4s.llm.model.Tool
import io.circe.generic.auto.given
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description

@description("X coordinate to tap")
case class TapInput(
    @description("X coordinate on screen")
    x: Int,
    @description("Y coordinate on screen")
    y: Int,
    @description("Optional device ID, if not provided uses default device")
    deviceId: Option[String] = None
)

case class TapOutput(success: Boolean, message: String)

/** Tap at specific screen coordinates
  *
  * Use this to click on UI elements at known positions.
  */
class TapTool[F[_]: Async] extends Tool[F, TapInput, TapOutput]:

  def name: String = "tap_screen"

  def description: String =
    "Tap at specific screen coordinates on the Android device"

  def execute(input: TapInput): F[TapOutput] =
    AdbBase
      .executeShell(s"input tap ${input.x} ${input.y}", input.deviceId)
      .map { output =>
        TapOutput(
          success = true,
          message = s"Tapped at (${input.x}, ${input.y})"
        )
      }
      .handleErrorWith { error =>
        Async[F].pure(
          TapOutput(
            success = false,
            message = s"Tap failed: ${error.getMessage}"
          )
        )
      }
