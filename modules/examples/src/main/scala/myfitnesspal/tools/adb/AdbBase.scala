package myfitnesspal.tools.adb

import cats.effect.kernel.Async
import myfitnesspal.model.{Device, DeviceStatus}
import scala.sys.process.*

/** Base utilities for ADB operations */
private[tools] object AdbBase:

  /** Execute a shell command on device */
  def executeShell[F[_]: Async](
      cmd: String,
      deviceId: Option[String]
  ): F[String] =
    Async[F].blocking {
      val deviceFlag = deviceId.map(id => s"-s $id ").getOrElse("")
      val fullCommand = s"adb ${deviceFlag}shell '$cmd'"
      println(s"[ADB] Executing: $fullCommand")
      Process(fullCommand).!!
    }

  /** List all connected devices */
  def listDevices[F[_]: Async](): F[List[Device]] =
    Async[F].blocking {
      val output = Process("adb devices -l").!!
      parseDeviceList(output)
    }

  /** Parse 'adb devices -l' output */
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
          val model = parts
            .find(_.startsWith("model:"))
            .map(_.stripPrefix("model:"))
          Some(Device(id, DeviceStatus.fromString(status), model))
        else None
      }
      .toList
