package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import cats.syntax.all.*
import no.marz.agent4s.llm.model.Tool
import io.circe.generic.auto.given
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description

case class SwipeInput(
    @description("Starting X coordinate")
    x1: Int,
    @description("Starting Y coordinate")
    y1: Int,
    @description("Ending X coordinate")
    x2: Int,
    @description("Ending Y coordinate")
    y2: Int,
    @description("Swipe duration in milliseconds (default 300)")
    durationMs: Option[Int] = None,
    @description("Optional device ID, if not provided uses default device")
    deviceId: Option[String] = None
)

case class SwipeOutput(success: Boolean, message: String)

/** Perform swipe gesture on screen
  *
  * Use this to scroll or swipe between screens.
  */
class SwipeTool[F[_]: Async] extends Tool[F, SwipeInput, SwipeOutput]:

  def name: String = "swipe_screen"

  def description: String =
    "Perform a swipe gesture on the Android device screen"

  def execute(input: SwipeInput): F[SwipeOutput] =
    val duration = input.durationMs.getOrElse(300)
    AdbBase
      .executeShell(
        s"input swipe ${input.x1} ${input.y1} ${input.x2} ${input.y2} $duration",
        input.deviceId
      )
      .map { _ =>
        SwipeOutput(
          success = true,
          message =
            s"Swiped from (${input.x1},${input.y1}) to (${input.x2},${input.y2})"
        )
      }
      .handleErrorWith { error =>
        Async[F].pure(
          SwipeOutput(
            success = false,
            message = s"Swipe failed: ${error.getMessage}"
          )
        )
      }
