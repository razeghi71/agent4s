package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import cats.syntax.all.*
import no.marz.agent4s.llm.model.Tool
import io.circe.generic.auto.given
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description
import scala.concurrent.duration.*

case class TypeTextInput(
    @description("Text to type into the focused field")
    text: String,
    @description("Optional device ID, if not provided uses default device")
    deviceId: Option[String] = None
)

case class TypeTextOutput(success: Boolean, message: String)

/** Type text into focused input field
  *
  * Types text character by character with small delays to avoid race conditions.
  * Make sure the target input field is focused before calling this.
  */
class TypeTextTool[F[_]: Async] extends Tool[F, TypeTextInput, TypeTextOutput]:

  def name: String = "type_text"

  def description: String =
    "Type text into the currently focused input field on Android device"

  /** Map character to keyevent code */
  private def charToKeyEvent(c: Char): Option[Int] = c.toLower match {
    case 'a' => Some(29)
    case 'b' => Some(30)
    case 'c' => Some(31)
    case 'd' => Some(32)
    case 'e' => Some(33)
    case 'f' => Some(34)
    case 'g' => Some(35)
    case 'h' => Some(36)
    case 'i' => Some(37)
    case 'j' => Some(38)
    case 'k' => Some(39)
    case 'l' => Some(40)
    case 'm' => Some(41)
    case 'n' => Some(42)
    case 'o' => Some(43)
    case 'p' => Some(44)
    case 'q' => Some(45)
    case 'r' => Some(46)
    case 's' => Some(47)
    case 't' => Some(48)
    case 'u' => Some(49)
    case 'v' => Some(50)
    case 'w' => Some(51)
    case 'x' => Some(52)
    case 'y' => Some(53)
    case 'z' => Some(54)
    case ' ' => Some(62)
    case '0' => Some(7)
    case '1' => Some(8)
    case '2' => Some(9)
    case '3' => Some(10)
    case '4' => Some(11)
    case '5' => Some(12)
    case '6' => Some(13)
    case '7' => Some(14)
    case '8' => Some(15)
    case '9' => Some(16)
    case _ => None // Skip unsupported characters
  }

  def execute(input: TypeTextInput): F[TypeTextOutput] =
    // Type each character individually with delays
    input.text.toCharArray.toList.traverse_ { char =>
      charToKeyEvent(char) match
        case Some(keyCode) =>
          AdbBase.executeShell(s"input keyevent $keyCode", input.deviceId) >>
          Async[F].sleep(50.millis)
        case None =>
          Async[F].unit
    }.map { _ =>
      TypeTextOutput(success = true, message = s"Typed: ${input.text}")
    }.handleErrorWith { error =>
      Async[F].pure(
        TypeTextOutput(success = false, message = s"Type failed: ${error.getMessage}")
      )
    }
