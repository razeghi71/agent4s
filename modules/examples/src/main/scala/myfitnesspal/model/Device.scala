package myfitnesspal.model

enum DeviceStatus:
  case Online, Offline, Unauthorized, Unknown

object DeviceStatus:
  def fromString(s: String): DeviceStatus = s match
    case "device"       => DeviceStatus.Online
    case "offline"      => DeviceStatus.Offline
    case "unauthorized" => DeviceStatus.Unauthorized
    case _              => DeviceStatus.Unknown

/** Represents an Android device or emulator connected via ADB
  *
  * @param id
  *   Device identifier (e.g., "emulator-5554")
  * @param status
  *   Connection status
  * @param model
  *   Device model name if available
  */
case class Device(
    id: String,
    status: DeviceStatus,
    model: Option[String] = None
):
  def isEmulator: Boolean = id.startsWith("emulator-")
  def isOnline: Boolean = status match
    case DeviceStatus.Online => true
    case _                   => false
