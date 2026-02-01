# MyFitnessPal Android Food Logger

An AI agent that automates food logging in the MyFitnessPal Android app using LLM reasoning and Android automation tools.

## Architecture

### Graph Flow

```
StartNode
    ↓
EnsureEmulatorNode (checks/launches Android emulator)
    ↓
LaunchAppNode (kills then launches MyFitnessPal for clean slate)
    ↓
ChatNode (LLM reasoning with tools) ←──┐
    ↓                                   │
    ├─ hasToolCalls? → ToolNode ───────┘
    │
    └─ otherwise → Terminal (done)
```

**Note**: User input is now handled via the `get_user_input` tool that the LLM can call directly, giving it full control over when to ask the user questions.

### State Management

`MyFitnessPalAgentState` tracks:
- **Emulator status**: running, deviceId, name
- **App status**: myFitnessPalOpen, onFoodSearchScreen
- **Food logging context**: currentMeal, searchQuery, searchResults, selectedFood
- **Conversation history**: messages (newest first)

### Tools Available to LLM

**Android Automation (`adb` package):**
- `tap_screen` - Tap at coordinates
- `type_text` - Type into focused field
- `swipe_screen` - Scroll/swipe gestures
- `press_key` - Press Android keys (BACK=4, ENTER=66)
- `launch_app` - Launch app by package name
- `kill_app` - Force stop app (for clean state)
- `get_ui_hierarchy` - Get current screen XML
- `execute_shell_command` - Arbitrary shell commands

**User Interaction:**
- `get_user_input` - Ask user a question and get their response

**UI Parsing:**
- `ui_parser` - Parse UI XML into structured elements

**Emulator Management:**
- `emulator_manager` - Check/launch emulator (used in graph nodes, not exposed to LLM)

## System Prompt

The LLM is given a comprehensive prompt (`SystemPrompt.scala`) that includes:

### 1. Pre-Processing
- Parse user's food list
- Correct typos and standardize names
- Ask for meal type (Breakfast/Lunch/Dinner/Snacks)
- Confirm date

### 2. Search Strategy
- Use corrected food names
- Scroll through results (don't settle for first)
- Try alternative search terms if needed
- Prefer verified items (checkmark)

### 3. Product Selection Criteria
- Match food type and preparation method
- **Must support user's unit** (g, oz, cup, etc.)
- Select different product if unit unavailable

### 4. Calorie Verification
Sanity check using ballpark ranges per 100g:
- Protein: ~120-200 cal
- Rice/grains: ~120-150 cal
- Vegetables: ~20-50 cal
- Yogurt: ~60-150 cal
- Oils/butter: ~700-900 cal

If wrong: check unit, amount, product selection

### 5. Ingredient Breakdown Fallback
If food not found:
- Break into component ingredients
- Log each separately
- Example: "100g tomato with butter" → 95g tomato + 5g butter

### 6. Final Summary
Provide table with all logged foods and total calories

## Tool Design Principles

### Why We Split AdbTool

The original `AdbTool` was problematic:
- ❌ Sealed trait inputs - LLMs struggle with discriminated unions
- ❌ One tool, 10+ commands - confuses the LLM
- ❌ Unclear outputs - sealed trait results

New design:
- ✅ **Single responsibility** - Each tool does one thing
- ✅ **Clean JSON schemas** - Simple case class inputs
- ✅ **Clear names** - `tap_screen`, not generic "adb"
- ✅ **Type-safe** - Structured outputs, no string parsing
- ✅ **LLM-friendly** - Clear descriptions and parameters

### Shared Base Utilities

`AdbBase.scala` provides common utilities:
- `executeShell()` - Run shell commands with device targeting
- `listDevices()` - Parse device list

These are private to the `adb` package and reused by all tools.

## Example Usage

```scala
// User input
"113g fried minced beef, 135g white rice cooked, 50g greek yogurt for lunch"

// Agent workflow:
1. Parses and corrects food names
2. Confirms meal type (Lunch) and date (Today)
3. Launches emulator if needed
4. Opens MyFitnessPal app
5. For each food:
   - Gets UI hierarchy
   - Finds search field and taps it
   - Types food name
   - Scrolls through results
   - Selects best match (verified, correct unit)
   - Enters amount
   - Verifies calories
   - Confirms
6. Provides summary table with total calories
```

## Key Features

### Intelligent Search
- Tries multiple search terms if needed
- Prefers verified results
- Scrolls through options

### Unit Matching
- Ensures selected product supports user's unit
- Selects different product if needed

### Calorie Verification
- Sanity checks against known nutritional ranges
- Flags suspicious values

### Error Handling
- Retries with alternative searches
- Falls back to ingredient breakdown
- Handles app crashes/freezes

### Human-in-the-Loop
- Shows corrected food list before proceeding
- Asks for meal type if not specified
- Provides final summary for confirmation

## Running

```bash
# Ensure emulator or device is available
adb devices

# Run the agent
sbt "examples/runMain myfitnesspal.myFitnessPalAgent"

# Or specify AVD name in code:
new EmulatorManagerTool[IO]("Pixel_6_API_36")
```

## Dependencies

- **Cats Effect** - Effect system
- **fs2** - Streaming
- **agent4s** - Graph and LLM abstractions
- **OpenAI API** - GPT-4 for reasoning
- **ADB** - Android Debug Bridge (must be in PATH)

## Future Enhancements

- [ ] Multi-turn conversation for disambiguation
- [ ] Photo-based food logging (OCR)
- [ ] Meal plan suggestions
- [ ] Nutritional insights and recommendations
- [ ] Support for other nutrition apps (Cronometer, Lose It!)
- [ ] Voice input integration
