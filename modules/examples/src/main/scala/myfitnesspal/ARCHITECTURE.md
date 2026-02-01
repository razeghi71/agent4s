# MyFitnessPal Android Agent - Complete Architecture

## System Overview

This is a complete AI agent implementation that automates food logging in MyFitnessPal Android app using:
- **LLM reasoning** (GPT-4) for decision making
- **Android automation** via ADB tools
- **Graph-based workflow** for state management
- **Human-in-the-loop** for confirmations

## Graph Architecture

```
                    START
                      ↓
          EnsureEmulatorNode (launch/check emulator)
                      ↓
            LaunchAppNode (kill + launch MyFitnessPal)
                      ↓
                  ChatNode ←────────┐
                (LLM reasoning)     │
                      ↓             │
              ┌───────┴────────┐    │
              │                │    │
        hasToolCalls?     no tools  │
              │                │    │
              ↓                ↓    │
          ToolNode         TERMINAL │
       (execute tools)               │
              │                      │
              └──────────────────────┘
```

**Key Change**: User input is now a tool (`get_user_input`) that the LLM calls directly, giving it full autonomy to decide when to interact with the user.

## Key Components

### 1. State (`MyFitnessPalAgentState`)

Tracks the entire workflow state:

```scala
case class MyFitnessPalAgentState(
  // Infrastructure
  emulatorRunning: Boolean,
  emulatorDeviceId: Option[String],
  emulatorName: Option[String],
  
  // App state
  myFitnessPalOpen: Boolean,
  onFoodSearchScreen: Boolean,
  
  // Food logging context
  currentMeal: Option[MealType],
  currentSearchQuery: Option[String],
  searchResults: List[FoodSearchResult],
  selectedFood: Option[FoodSearchResult],
  
  // Conversation
  messages: List[Message] // newest first
)
```

### 2. Nodes

**StartNode** - Initialization
- Prints welcome message
- Returns state unchanged

**EnsureEmulatorNode** - Infrastructure setup
- Checks if emulator running
- Launches if needed
- Updates state with device ID

**LaunchAppNode** - App launch with clean slate
- First kills MyFitnessPal via `kill_app` tool
- Waits 2 seconds for process termination
- Launches MyFitnessPal via monkey command
- Waits 5 seconds for app to fully load
- Updates state.myFitnessPalOpen

**ChatNode** - LLM reasoning
- Takes conversation history + system prompt
- Calls GPT-4 with all available tools
- Updates state with assistant response (text or tool calls)

**ToolNode** - Tool execution
- Extracts tool calls from last assistant message
- Executes all tools in parallel
- Handles `get_user_input` tool for user interaction
- Updates state with tool results
- Returns to ChatNode

**Removed: UserInputNode** - Now handled via `get_user_input` tool

### 3. Tools (available to LLM)

#### Android Automation

**TapTool** - `tap_screen`
```json
{
  "x": 540,
  "y": 960,
  "deviceId": "emulator-5554"
}
```

**TypeTextTool** - `type_text`
```json
{
  "text": "chicken breast",
  "deviceId": "emulator-5554"
}
```

**SwipeTool** - `swipe_screen`
```json
{
  "x1": 540,
  "y1": 1500,
  "x2": 540,
  "y2": 500,
  "durationMs": 300,
  "deviceId": "emulator-5554"
}
```

**PressKeyTool** - `press_key`
```json
{
  "keyCode": 4,  // BACK=4, ENTER=66, HOME=3
  "deviceId": "emulator-5554"
}
```

**LaunchAppTool** - `launch_app`
```json
{
  "packageName": "com.myfitnesspal.android",
  "deviceId": "emulator-5554"
}
```

**KillAppTool** - `kill_app`
```json
{
  "packageName": "com.myfitnesspal.android",
  "deviceId": "emulator-5554"
}
```

**GetUITool** - `get_ui_hierarchy`
```json
{
  "deviceId": "emulator-5554"
}
```
Returns: XML structure of current screen

**ExecuteShellTool** - `execute_shell_command` (fallback)
```json
{
  "command": "pm list packages | grep myfitnesspal",
  "deviceId": "emulator-5554"
}
```

#### User Interaction

**GetUserInputTool** - `get_user_input`
```json
{
  "prompt": "I've corrected the food list:\n- Chicken breast, 113g\n- White rice (cooked), 135g\n\nIs this correct?"
}
```
Returns: User's text response

The LLM can call this tool whenever it needs:
- Confirmation of corrected food list
- Meal type selection
- Verification of calories
- Final summary approval

#### UI Parsing

**UIParserTool** - `ui_parser`
```json
{
  "xmlContent": "<hierarchy>...</hierarchy>"
}
```
Returns: Structured UIElement tree with bounds, text, etc.

### 4. System Prompt

Comprehensive instructions including:

**Pre-processing**
- Parse food list
- Correct typos
- Ask for meal type

**Search strategy**
- Try multiple search terms
- Scroll through results
- Prefer verified items

**Product selection**
- Match food type, prep method
- Ensure unit support (critical!)
- Verify calories

**Error handling**
- Alternative searches
- Ingredient breakdown fallback
- App crash recovery

**Communication**
- Clear progress updates
- Ask for confirmations
- Provide final summary table

## Example Execution Flow

### User Input
```
"113g fried minced beef, 135g white rice cooked, 50g greek yogurt for lunch"
```

### Agent Actions

1. **ChatNode**: Parses input
   - Output: "I'll log these foods to lunch: fried minced beef (113g), white rice cooked (135g), greek yogurt (50g). Is this correct?"

2. **UserInputNode**: Waits for confirmation
   - User: "yes"

3. **ChatNode**: Decides next action
   - Output: ToolCalls[get_ui_hierarchy]

4. **ToolNode**: Gets UI
   - Returns: XML of current screen

5. **ChatNode**: Analyzes UI, decides to search for first food
   - Output: ToolCalls[tap_screen(x=540, y=200), type_text("fried minced beef")]

6. **ToolNode**: Executes tap and type
   - Returns: success messages

7. **ChatNode**: Gets UI to see search results
   - Output: ToolCalls[get_ui_hierarchy]

8. **ToolNode**: Returns search results UI

9. **ChatNode**: Parses results, selects best match
   - Output: ToolCalls[tap_screen(x=540, y=500)]

... continues for each food ...

10. **ChatNode**: Final summary
    - Output: "Logged to MyFitnessPal - Lunch - Today:\n\nFood | Amount | Calories\n..."

11. **UserInputNode**: No question detected
    - Goes to Terminal

## Key Design Decisions

### 1. Split AdbTool into Focused Tools
**Why**: LLMs perform better with single-purpose tools
- Each tool has clear name and purpose
- Simple JSON schemas
- No sealed traits or discriminated unions

### 2. User Input as Tool (Not Graph Node)
**Why**: Gives LLM full control over conversation flow
- LLM decides when to ask questions
- More flexible than hardcoded detection
- Can ask multiple questions in sequence
- Natural part of tool execution flow

### 3. Kill App Before Launch
**Why**: Ensures clean state every time
- No cached screens or stale data
- Predictable starting point
- Avoids navigation from unknown state
- Prevents issues from previous runs

### 4. Messages Stored Newest-First
**Why**: Efficient prepending in functional style
- New messages added to head of list
- Reversed when sending to LLM
- Common pattern in functional programming

### 4. Messages Stored Newest-First
**Why**: Efficient prepending in functional style
- New messages added to head of list
- Reversed when sending to LLM
- Common pattern in functional programming

### 5. Comprehensive System Prompt
**Why**: Better than relying on few-shot examples
- Complete workflow documented
- Nutritional knowledge embedded
- Error handling strategies included
- UI navigation tips provided

### 5. Tool Results Include Success/Failure
**Why**: LLM can handle errors gracefully
- Structured error messages
- Agent can retry with different approach
- No crashes from tool failures

## Running the Agent

```bash
# Ensure ADB is available
which adb

# Check devices
adb devices

# Run agent
sbt "examples/runMain myfitnesspal.myFitnessPalAgent"
```

### Configuration

Edit `MyFitnessPalAgent.scala`:

```scala
// Change emulator name
new EmulatorManagerTool[IO]("Pixel_6_API_36")

// Change LLM model
model = "gpt-4o-mini"  // cheaper, faster

// Change app package
packageName = "com.myfitnesspal.android"
```

## File Structure

```
myfitnesspal/
├── MyFitnessPalAgent.scala    # Main entry point, graph definition
├── SystemPrompt.scala          # LLM instructions
├── README.md                   # User documentation
├── ARCHITECTURE.md             # This file
├── model/
│   ├── State.scala            # AgentState definition
│   ├── Bounds.scala           # UI coordinates
│   ├── Device.scala           # Android device model
│   └── UIElement.scala        # Parsed UI element
└── tools/
    ├── adb/
    │   ├── AdbBase.scala          # Shared ADB utilities
    │   ├── AdbTools.scala         # Export all tools
    │   ├── TapTool.scala          # Tap at coordinates
    │   ├── TypeTextTool.scala     # Type text
    │   ├── SwipeTool.scala        # Swipe gesture
    │   ├── PressKeyTool.scala     # Press key codes
    │   ├── LaunchAppTool.scala    # Launch apps
    │   ├── KillAppTool.scala      # Force stop apps
    │   ├── GetUITool.scala        # Get UI hierarchy
    │   └── ExecuteShellTool.scala # Shell commands
    ├── GetUserInputTool.scala      # Get user input (NEW!)
    ├── UIParserTool.scala         # Parse UI XML
    ├── EmulatorManagerTool.scala  # Emulator lifecycle
    ├── FoodNormalizerTool.scala   # TODO: LLM-based normalization
    └── FoodMatcherTool.scala      # TODO: LLM-based matching
```

## Future Enhancements

### Short Term
- [ ] Implement FoodNormalizerTool with actual LLM call
- [ ] Implement FoodMatcherTool with actual LLM call
- [ ] Add retry logic for transient failures
- [ ] Add screenshots at key steps for debugging

### Medium Term
- [ ] Multi-device support (parallel logging)
- [ ] Voice input via speech-to-text
- [ ] Photo-based food logging (OCR + vision models)
- [ ] Meal plan generation

### Long Term
- [ ] Support other nutrition apps (Cronometer, Lose It)
- [ ] Nutritional analysis and recommendations
- [ ] Recipe tracking and ingredients
- [ ] Restaurant menu integration

## Debugging

### Enable verbose logging
```scala
// Add to ChatNode
println(s"Request: ${request.asJson.spaces2}")
println(s"Response: ${response.asJson.spaces2}")
```

### Capture UI at each step
```scala
// Add to graph execution
.evalTap { state =>
  IO {
    println(s"\n=== State after ${currentNode} ===")
    println(state.messages.headOption)
  }
}
```

### Test individual tools
```scala
val tapTool = new TapTool[IO]
tapTool.execute(TapInput(540, 960, None)).unsafeRunSync()
```

## Contributing

This implementation demonstrates:
- ✅ Clean architecture with separation of concerns
- ✅ Type-safe tool definitions
- ✅ Functional effects with Cats Effect
- ✅ Graph-based workflow with clear state transitions
- ✅ LLM-friendly tool design
- ✅ Human-in-the-loop interaction
- ✅ Comprehensive error handling

It serves as a reference implementation for building AI agents with Android automation.
