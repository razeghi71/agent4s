package myfitnesspal.tools.adb

/** All ADB tools for interacting with Android devices
  *
  * These tools are split into focused, single-purpose operations that are
  * easier for LLMs to use correctly.
  *
  * Available tools:
  *   - TapTool: Tap at screen coordinates
  *   - TypeTextTool: Type text into focused field
  *   - SwipeTool: Perform swipe gestures
  *   - PressKeyTool: Press Android key codes
  *   - LaunchAppTool: Launch apps by package name
  *   - KillAppTool: Force stop apps
  *   - GetUITool: Get current UI hierarchy as XML
  *   - ExecuteShellTool: Execute arbitrary shell commands (fallback)
  */
object AdbTools
