package myfitnesspal.tools

import cats.effect.kernel.Async
import cats.syntax.all.*
import scala.concurrent.duration.*
import myfitnesspal.model.Device
import myfitnesspal.tools.adb.AdbBase
import scala.sys.process.*
import no.marz.agent4s.llm.model.Tool
import io.circe.generic.auto.given
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given

/** Input for EmulatorManagerTool - empty, just triggers check */
case class EmulatorCheckInput()

/** Status of the Android emulator
  *
  * @param running
  *   Whether the emulator is currently running
  * @param deviceId
  *   Device ID if running (e.g., "emulator-5554")
  * @param emulatorName
  *   AVD name if available
  */
case class EmulatorStatus(
    running: Boolean,
    deviceId: Option[String],
    emulatorName: Option[String]
)

/** Manages Android emulator lifecycle
  *
  * Checks if emulator is running and launches it if needed.
  *
  * @param avdName
  *   AVD name to launch (e.g., "Pixel_6_API_36")
  * @param launchTimeout
  *   Maximum time to wait for emulator to boot
  * @param bootCheckInterval
  *   How often to check if emulator has booted
  */
class EmulatorManagerTool[F[_]: Async](
    avdName: String,
    launchTimeout: FiniteDuration = 60.seconds,
    bootCheckInterval: FiniteDuration = 5.seconds
) extends Tool[F, EmulatorCheckInput, EmulatorStatus]:

  def name: String = "emulator_manager"

  def description: String =
    "Check emulator status and launch if needed"

  def execute(input: EmulatorCheckInput): F[EmulatorStatus] =
    for {
      _ <- Async[F].delay(
        println("Checking emulator status...")
      )
      devices <- listDevices()
      emulator = devices.find(_.isEmulator)

      result <- emulator match
        case Some(device) if device.isOnline =>
          // Emulator already running
          Async[F].delay {
            println(
              s"Emulator already running: ${device.id}${device.model.map(m => s" ($m)").getOrElse("")}"
            )
            EmulatorStatus(
              running = true,
              deviceId = Some(device.id),
              emulatorName = device.model
            )
          }

        case Some(device) =>
          // Emulator exists but offline
          Async[F].delay {
            println(
              s"Emulator ${device.id} is offline, launching fresh instance..."
            )
          } *> launchAndWait()

        case None =>
          // No emulator found, need to launch
          Async[F].delay {
            println(s"Emulator not running, launching $avdName...")
          } *> launchAndWait()

    } yield result

  /** List all devices using ADB */
  private def listDevices(): F[List[Device]] =
    AdbBase.listDevices[F]()

  /** Launch emulator and wait for it to boot */
  private def launchAndWait(): F[EmulatorStatus] =
    for {
      _ <- launchEmulator()
      _ <- Async[F].delay(
        println(s"Waiting for emulator to boot (timeout: ${launchTimeout.toSeconds}s)...")
      )
      deviceId <- waitForEmulatorBoot()
      _ <- Async[F].delay(
        println(s"✓ Emulator booted successfully: $deviceId")
      )
    } yield EmulatorStatus(
      running = true,
      deviceId = Some(deviceId),
      emulatorName = Some(avdName)
    )

  /** Launch the emulator in background
    *
    * Uses optimized launch command for Apple Silicon with hardware GPU
    * acceleration
    */
  private def launchEmulator(): F[Unit] =
    Async[F].blocking {
      // Set up environment variables for M1 optimization
      val androidHome = "/opt/homebrew/share/android-commandlinetools"
      val emulatorPath = s"$androidHome/emulator/emulator"
      
      val env = Seq(
        "ANDROID_HOME" -> androidHome,
        "ANDROID_SDK_ROOT" -> androidHome,
        "ANDROID_EMULATOR_USE_SYSTEM_LIBS" -> "0"
      )
      
      // Use the same optimized flags as user's script
      val command = Seq(
        emulatorPath,
        "-avd", avdName,
        "-gpu", "host",     // Full Metal/Vulkan acceleration for M1
        "-no-metrics"       // Disable metrics
      )
      
      // Launch in background
      val process = Process(command, None, env*).run()
      println(s"Emulator launch command executed: ${command.mkString(" ")}")
      println(s"Environment: ${env.map { case (k, v) => s"$k=$v" }.mkString(", ")}")
    }.handleErrorWith { error =>
      Async[F].raiseError(
        new RuntimeException(
          s"Failed to launch emulator: ${error.getMessage}. " +
            s"Make sure emulator is at /opt/homebrew/share/android-commandlinetools/emulator/emulator " +
            s"and AVD '$avdName' exists."
        )
      )
    }

  /** Poll for emulator to appear in device list and become ready
    *
    * Checks every bootCheckInterval until emulator is online or timeout
    * reached
    */
  private def waitForEmulatorBoot(): F[String] =
    def checkBoot(
        attempts: Int,
        maxAttempts: Int
    ): F[String] =
      if attempts >= maxAttempts then
        Async[F].raiseError(
          new RuntimeException(
            s"Emulator failed to boot within ${launchTimeout.toSeconds} seconds"
          )
        )
      else
        listDevices().flatMap { devices =>
          devices.find(d => d.isEmulator && d.isOnline) match
            case Some(device) =>
              Async[F].pure(device.id)
            case None =>
              // Not ready yet, wait and retry
              Async[F].sleep(bootCheckInterval) *>
                checkBoot(attempts + 1, maxAttempts)
        }

    val maxAttempts = (launchTimeout.toMillis / bootCheckInterval.toMillis).toInt
    checkBoot(0, maxAttempts)
