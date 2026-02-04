# MyFitnessPal Android Food Logger

An AI agent that automates food logging in the MyFitnessPal Android app. It uses an LLM (DeepSeek) for reasoning, ADB for Android automation, and a graph-based workflow for state management.

## Prerequisites

- **ADB** installed and on your PATH (`which adb`)
- **Android emulator** with an AVD created (e.g., via Android Studio AVD Manager)
- **MyFitnessPal** installed on the emulator with a **logged-in account** - the agent does not handle login/signup, so you must manually install the app and log in before running

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DEEPSEEK_API_KEY` | Yes | DeepSeek API key |
| `ANDROID_HOME` | Yes | Path to Android SDK (e.g., `/opt/homebrew/share/android-commandlinetools` on macOS with Homebrew, or `~/Android/Sdk` on Linux) |
| `AVD_NAME` | Yes | Name of your AVD (e.g., `Pixel_6_API_36`). Find yours with `emulator -list-avds` |
| `DEEPSEEK_MODEL` | No | Model to use (default: `deepseek-chat`) |

Example setup:
```bash
export DEEPSEEK_API_KEY="your-api-key"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"  # macOS Homebrew
export AVD_NAME="Pixel_6_API_36"
```

The agent will launch the emulator automatically if it's not already running.

## Running

```bash
sbt "examples/runMain myfitnesspal.myFitnessPalAgent"
```

You'll be prompted to enter foods, e.g.:

```
> 113g fried minced beef, 135g white rice cooked, 50g greek yogurt
```

## Architecture

### Graph Flow

```
StartNode
    |
EnsureEmulatorNode  -- launches emulator if not running
    |
LaunchAppNode       -- kills then launches MyFitnessPal
    |
ChatNode  <---------+
  (LLM reasoning)   |
    |                |
    +-- tool calls? -+-> ToolNode (execute tools, loop back)
    |
    +-- no tools ------> Terminal (done)
```

User interaction is handled via the `get_user_input` tool that the LLM calls directly, giving it full control over when to ask questions.

### State

`MyFitnessPalAgentState` tracks:
- Emulator status (running, deviceId, name)
- App status (myFitnessPalOpen)
- Conversation history (messages, newest-first)

All food logging context (meal type, search results, selected foods) is managed by the LLM through its conversation history rather than explicit state fields.

### Tools

**Android automation** (`tools/adb/`):

| Tool | Name | Description |
|------|------|-------------|
| `TapTool` | `tap_screen` | Tap at screen coordinates |
| `TypeTextTool` | `type_text` | Type into focused input field |
| `SwipeTool` | `swipe_screen` | Swipe/scroll gestures |
| `PressKeyTool` | `press_key` | Press Android keys (BACK=4, ENTER=66, HOME=3) |
| `LaunchAppTool` | `launch_app` | Launch app by package name |
| `KillAppTool` | `kill_app` | Force stop app |
| `GetUITool` | `get_ui` | Get compact UI hierarchy of current screen |
| `ExecuteShellTool` | `execute_shell_command` | Run arbitrary shell commands |

**User interaction** (`tools/`):

| Tool | Name | Description |
|------|------|-------------|
| `GetUserInputTool` | `get_user_input` | Ask the user a question |

**Infrastructure** (used by graph nodes, not exposed to LLM):

| Tool | Name | Description |
|------|------|-------------|
| `EmulatorManagerTool` | `emulator_manager` | Check/launch emulator |

`GetUITool` returns a compact representation of actionable elements (buttons, text, inputs) with their screen coordinates, reducing token usage compared to raw XML. Format: `[id] label (class) [actions] @ [bounds]`.

### Models

- `Device` -- represents an ADB-connected device with a `DeviceStatus` enum (`Online`, `Offline`, `Unauthorized`, `Unknown`)
- `UIElement` -- parsed UI tree node with bounds, text, resource IDs, and child elements
- `Bounds` -- screen coordinate rectangle

### Shared Utilities

`AdbBase` (package-private) provides `executeShell()` and `listDevices()` reused by all ADB tools.

## System Prompt

The LLM receives detailed instructions (`SystemPrompt.scala`) covering:

1. **Pre-processing** -- parse and correct food names, confirm meal type and date
2. **Search strategy** -- try multiple terms, scroll results, prefer verified items
3. **Product selection** -- match food type, preparation method, and ensure the product supports the user's unit (g, oz, cup, etc.)
4. **Calorie verification** -- sanity check against known ranges per 100g (protein ~150 cal, rice ~130 cal, vegetables ~30 cal, etc.)
5. **Ingredient breakdown fallback** -- if a food isn't found, decompose it into components and log each separately
6. **Final summary** -- table of logged foods with total calories

## File Structure

```
myfitnesspal/
  MyFitnessPalAgent.scala       -- entry point, graph definition, nodes
  SystemPrompt.scala            -- LLM instructions
  model/
    MyFitnessPalAgentState.scala -- workflow state
    Device.scala                 -- device model + DeviceStatus enum
    UIElement.scala              -- parsed UI element tree
    Bounds.scala                 -- screen coordinates
  tools/
    GetUserInputTool.scala       -- user interaction
    EmulatorManagerTool.scala    -- emulator lifecycle
    adb/
      AdbBase.scala              -- shared ADB utilities
      AdbTools.scala             -- re-exports all ADB tools
      TapTool.scala
      TypeTextTool.scala
      SwipeTool.scala
      PressKeyTool.scala
      LaunchAppTool.scala
      KillAppTool.scala
      GetUITool.scala
      ExecuteShellTool.scala
```

## Design Decisions

**Single-purpose tools over monolithic AdbTool** -- LLMs perform better with focused tools that have clear names and simple JSON schemas rather than a single tool with discriminated union inputs.

**User input as a tool** -- gives the LLM full autonomy over conversation flow instead of hardcoding interaction points in the graph.

**Kill before launch** -- ensures a clean app state every run, avoiding navigation from unknown screens.

**Newest-first message list** -- efficient prepending in functional style; reversed when sending to the LLM.

**Compact UI representation** -- `GetUITool` filters to actionable/informative elements only, cutting token usage by ~86% compared to raw XML.
