package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import cats.syntax.all.*
import no.marz.agent4s.llm.model.Tool
import io.circe.generic.auto.given
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description

case class PressKeyInput(
    @description("Android key code to press (e.g., 4=BACK, 3=HOME, 66=ENTER)")
    keyCode: Int,
    @description("Optional device ID, if not provided uses default device")
    deviceId: Option[String] = None
)

case class PressKeyOutput(success: Boolean, message: String)

/** Press a key using Android key code
  *
  * Common key codes:
  *   - 4: BACK
  *   - 3: HOME
  *   - 66: ENTER
  *   - 67: DEL (backspace)
  *   - 82: MENU
  */
class PressKeyTool[F[_]: Async] extends Tool[F, PressKeyInput, PressKeyOutput]:

  def name: String = "press_key"

  def description: String =
    "Press a key on the Android device using key code"

  def execute(input: PressKeyInput): F[PressKeyOutput] =
    AdbBase
      .executeShell(s"input keyevent ${input.keyCode}", input.deviceId)
      .map { _ =>
        PressKeyOutput(
          success = true,
          message = s"Pressed key code ${input.keyCode}"
        )
      }
      .handleErrorWith { error =>
        Async[F].pure(
          PressKeyOutput(
            success = false,
            message = s"Key press failed: ${error.getMessage}"
          )
        )
      }
