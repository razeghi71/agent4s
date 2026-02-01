package myfitnesspal.tools

import cats.effect.kernel.Async
import cats.syntax.all.*
import myfitnesspal.model.{Device, Bounds}
import scala.sys.process.*
import scala.concurrent.duration.*
import java.io.File
import no.marz.agent4s.llm.model.Tool
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given

/** ADB commands that can be executed */
sealed trait AdbCommand

object AdbCommand:
  /** List all connected devices */
  case object ListDevices extends AdbCommand

  /** Execute a shell command on device */
  case class Shell(command: String) extends AdbCommand

  /** Dump UI hierarchy to XML */
  case object DumpUI extends AdbCommand

  /** Pull the UI dump from device */
  case object PullUIDump extends AdbCommand

  /** Tap at coordinates */
  case class Tap(x: Int, y: Int) extends AdbCommand

  /** Swipe gesture */
  case class Swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int)
      extends AdbCommand

  /** Input text */
  case class Input(text: String) extends AdbCommand

  /** Send key event */
  case class KeyEvent(keyCode: Int) extends AdbCommand

  /** Start an activity */
  case class StartActivity(packageName: String, activity: String)
      extends AdbCommand

  /** Launch app via package name using monkey (bypasses permission issues) */
  case class LaunchApp(packageName: String) extends AdbCommand

  /** Take a screenshot and save locally */
  case class Screenshot(localPath: String) extends AdbCommand

/** Result of ADB command execution */
sealed trait AdbResult

object AdbResult:
  case class CommandSuccess(output: String) extends AdbResult
  case class CommandFailure(error: String) extends AdbResult
  case class UIXml(content: String) extends AdbResult
  case class DeviceList(devices: List[Device]) extends AdbResult

/** Input for AdbTool
  *
  * @param command
  *   The ADB command to execute
  * @param deviceId
  *   Optional device ID, if None uses default device
  */
case class AdbToolInput(
    command: AdbCommand,
    deviceId: Option[String] = None
)

case class AdbToolOutput(result: AdbResult)

object AdbToolInput:
  given Encoder[AdbToolInput] = Encoder.instance { input =>
    Json.obj(
      "command" -> Json.fromString(input.command.toString),
      "deviceId" -> input.deviceId.fold(Json.Null)(Json.fromString)
    )
  }
  given Decoder[AdbToolInput] = Decoder.instance { cursor =>
    // Simplified decoder for tool registry compatibility
    Right(AdbToolInput(AdbCommand.Shell(""), None))
  }

object AdbToolOutput:
  given Encoder[AdbToolOutput] = Encoder.instance { output =>
    Json.obj("result" -> Json.fromString(output.result.toString))
  }

/** Low-level ADB tool for Android device/emulator interactions
  *
  * Executes ADB commands and returns structured results. Assumes `adb` is in
  * PATH.
  */
class AdbTool[F[_]: Async] extends Tool[F, AdbToolInput, AdbToolOutput]:

  def name: String = "adb"

  def description: String =
    "Execute ADB commands on Android device or emulator"

  /** Map character to keyevent command for input */
  private def charToKeyEvent(c: Char): String = c.toLower match {
    case 'a' => "input keyevent 29"
    case 'b' => "input keyevent 30"
    case 'c' => "input keyevent 31"
    case 'd' => "input keyevent 32"
    case 'e' => "input keyevent 33"
    case 'f' => "input keyevent 34"
    case 'g' => "input keyevent 35"
    case 'h' => "input keyevent 36"
    case 'i' => "input keyevent 37"
    case 'j' => "input keyevent 38"
    case 'k' => "input keyevent 39"
    case 'l' => "input keyevent 40"
    case 'm' => "input keyevent 41"
    case 'n' => "input keyevent 42"
    case 'o' => "input keyevent 43"
    case 'p' => "input keyevent 44"
    case 'q' => "input keyevent 45"
    case 'r' => "input keyevent 46"
    case 's' => "input keyevent 47"
    case 't' => "input keyevent 48"
    case 'u' => "input keyevent 49"
    case 'v' => "input keyevent 50"
    case 'w' => "input keyevent 51"
    case 'x' => "input keyevent 52"
    case 'y' => "input keyevent 53"
    case 'z' => "input keyevent 54"
    case ' ' => "input keyevent 62"
    case '0' => "input keyevent 7"
    case '1' => "input keyevent 8"
    case '2' => "input keyevent 9"
    case '3' => "input keyevent 10"
    case '4' => "input keyevent 11"
    case '5' => "input keyevent 12"
    case '6' => "input keyevent 13"
    case '7' => "input keyevent 14"
    case '8' => "input keyevent 15"
    case '9' => "input keyevent 16"
    case _ => "" // Skip unsupported characters
  }

  def execute(input: AdbToolInput): F[AdbToolOutput] =
    import AdbCommand.*
    import AdbResult.*

    input.command match
      case ListDevices =>
        executeListDevices()

      case Shell(cmd) =>
        executeShell(cmd, input.deviceId)

      case DumpUI =>
        executeDumpUI(input.deviceId)

      case PullUIDump =>
        executePullUIDump(input.deviceId)

      case Tap(x, y) =>
        executeShell(s"input tap $x $y", input.deviceId)

      case Swipe(x1, y1, x2, y2, durationMs) =>
        executeShell(s"input swipe $x1 $y1 $x2 $y2 $durationMs", input.deviceId)

      case Input(text) =>
        // Type each character individually with delays to avoid UI race conditions
        // where characters appear in wrong order
        text.toCharArray.toList.traverse_ { char =>
          val keyEvent = charToKeyEvent(char)
          if (keyEvent.nonEmpty) {
            executeShell(keyEvent, input.deviceId) >>
            Async[F].sleep(50.millis)  // Small delay between characters
          } else {
            Async[F].unit
          }
        } >> Async[F].pure(AdbToolOutput(AdbResult.CommandSuccess("")))

      case KeyEvent(keyCode) =>
        executeShell(s"input keyevent $keyCode", input.deviceId)

      case StartActivity(pkg, activity) =>
        executeShell(s"am start -n $pkg/$activity", input.deviceId)

      case LaunchApp(pkg) =>
        executeShell(
          s"monkey -p $pkg -c android.intent.category.LAUNCHER 1",
          input.deviceId
        )

      case Screenshot(localPath) =>
        executeScreenshot(localPath, input.deviceId)

  /** Execute: adb devices -l and parse output */
  private def executeListDevices(): F[AdbToolOutput] =
    Async[F].blocking {
      val output = Process("adb devices -l").!!
      val devices = parseDeviceList(output)
      AdbToolOutput(AdbResult.DeviceList(devices))
    }.handleErrorWith { error =>
      Async[F].pure(
        AdbToolOutput(
          AdbResult.CommandFailure(s"Failed to list devices: ${error.getMessage}")
        )
      )
    }

  /** Parse 'adb devices -l' output
    *
    * Example output: List of devices attached emulator-5554 device product:sdk_gphone64_arm64
    * model:Pixel_6_API_36 device:emu64a transport_id:1
    */
  private def parseDeviceList(output: String): List[Device] =
    output
      .split("\n")
      .drop(1) // Skip "List of devices attached" header
      .filter(_.trim.nonEmpty)
      .flatMap { line =>
        val parts = line.trim.split("\\s+")
        if parts.length >= 2 then
          val id = parts(0)
          val status = parts(1)
          // Extract model if present (e.g., "model:Pixel_6_API_36")
          val model = parts
            .find(_.startsWith("model:"))
            .map(_.stripPrefix("model:"))
          Some(Device(id, status, model))
        else None
      }
      .toList

  /** Execute a shell command on device */
  private def executeShell(
      cmd: String,
      deviceId: Option[String]
  ): F[AdbToolOutput] =
    Async[F].blocking {
      val deviceFlag = deviceId.map(id => s"-s $id ").getOrElse("")
      val fullCommand = s"adb ${deviceFlag}shell '$cmd'"
      println(s"[AdbTool] Executing: $fullCommand")
      val output = Process(fullCommand).!!
      AdbToolOutput(AdbResult.CommandSuccess(output))
    }.handleErrorWith { error =>
      Async[F].pure(
        AdbToolOutput(
          AdbResult.CommandFailure(
            s"Shell command failed: ${error.getMessage}"
          )
        )
      )
    }

  /** Dump UI hierarchy to device storage */
  private def executeDumpUI(deviceId: Option[String]): F[AdbToolOutput] =
    Async[F].blocking {
      val deviceFlag = deviceId.map(id => s"-s $id ").getOrElse("")
      val cmd = s"adb ${deviceFlag}shell uiautomator dump"
      val output = Process(cmd).!!
      AdbToolOutput(AdbResult.CommandSuccess(output))
    }.handleErrorWith { error =>
      Async[F].pure(
        AdbToolOutput(
          AdbResult.CommandFailure(s"UI dump failed: ${error.getMessage}")
        )
      )
    }

  /** Pull UI dump from device and return content */
  private def executePullUIDump(deviceId: Option[String]): F[AdbToolOutput] =
    Async[F].blocking {
      val deviceFlag = deviceId.map(id => s"-s $id ").getOrElse("")
      val tempFile = s"/tmp/ui_dump_${System.currentTimeMillis()}.xml"

      // Pull file from device
      val pullCmd = s"adb ${deviceFlag}pull /sdcard/window_dump.xml $tempFile"
      Process(pullCmd).!!

      // Read file content
      val content = scala.io.Source.fromFile(tempFile).mkString
      new File(tempFile).delete() // Cleanup

      AdbToolOutput(AdbResult.UIXml(content))
    }.handleErrorWith { error =>
      Async[F].pure(
        AdbToolOutput(
          AdbResult.CommandFailure(
            s"Failed to pull UI dump: ${error.getMessage}"
          )
        )
      )
    }

  /** Take screenshot and save to local path */
  private def executeScreenshot(
      localPath: String,
      deviceId: Option[String]
  ): F[AdbToolOutput] =
    Async[F].blocking {
      val deviceFlag = deviceId.map(id => s"-s $id ").getOrElse("")
      // Create directory if it doesn't exist
      val file = new File(localPath)
      file.getParentFile.mkdirs()

      val cmd = s"adb ${deviceFlag}exec-out screencap -p"
      val output = Process(cmd).#>(file).!!

      AdbToolOutput(AdbResult.CommandSuccess(s"Screenshot saved to $localPath"))
    }.handleErrorWith { error =>
      Async[F].pure(
        AdbToolOutput(
          AdbResult.CommandFailure(
            s"Screenshot failed: ${error.getMessage}"
          )
        )
      )
    }
