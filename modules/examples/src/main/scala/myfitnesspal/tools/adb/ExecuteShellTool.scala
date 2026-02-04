package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import cats.syntax.all.*
import no.marz.agent4s.llm.model.Tool
import io.circe.generic.auto.given
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description

case class ExecuteShellInput(
    @description("Shell command to execute on the device")
    command: String,
    @description("Optional device ID, if not provided uses default device")
    deviceId: Option[String] = None
)

case class ExecuteShellOutput(
    success: Boolean,
    output: Option[String],
    errorMessage: Option[String]
)

/** Execute arbitrary shell command on Android device
  *
  * Use this for commands not covered by specific tools. Be careful with shell
  * syntax and escaping.
  */
class ExecuteShellTool[F[_]: Async]
    extends Tool[F, ExecuteShellInput, ExecuteShellOutput]:

  def name: String = "execute_shell_command"

  def description: String =
    "Execute an arbitrary shell command on the Android device"

  def execute(input: ExecuteShellInput): F[ExecuteShellOutput] =
    AdbBase
      .executeShell(input.command, input.deviceId)
      .map { output =>
        ExecuteShellOutput(
          success = true,
          output = Some(output),
          errorMessage = None
        )
      }
      .handleErrorWith { error =>
        Async[F].pure(
          ExecuteShellOutput(
            success = false,
            output = None,
            errorMessage = Some(error.getMessage)
          )
        )
      }
