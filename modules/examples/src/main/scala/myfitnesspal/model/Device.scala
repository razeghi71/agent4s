package myfitnesspal.model

/** Represents an Android device or emulator connected via ADB
  *
  * @param id
  *   Device identifier (e.g., "emulator-5554")
  * @param status
  *   Connection status (e.g., "device", "offline")
  * @param model
  *   Device model name if available
  */
case class Device(
    id: String,
    status: String,
    model: Option[String] = None
):
  def isEmulator: Boolean = id.startsWith("emulator-")
  def isOnline: Boolean = status == "device"
