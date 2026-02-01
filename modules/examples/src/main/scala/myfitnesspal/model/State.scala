package myfitnesspal.model

import no.marz.agent4s.graph.GraphState
import no.marz.agent4s.llm.model.{Message, AssistantContent, ToolCall}

/** Meal types in MyFitnessPal */
enum MealType:
  case Breakfast, Lunch, Dinner, Snacks

/** A food item from search results
  *
  * @param name
  *   Food name (e.g., "Egg")
  * @param description
  *   Calorie and serving info (e.g., "72 cal, 1.0 egg")
  * @param verified
  *   Whether food has verified checkmark
  * @param bounds
  *   UI bounds for tapping
  * @param calories
  *   Parsed calorie count
  * @param servingSize
  *   Parsed serving size (e.g., "1.0")
  * @param servingUnit
  *   Parsed serving unit (e.g., "egg", "large")
  */
case class FoodSearchResult(
    name: String,
    description: String,
    verified: Boolean,
    bounds: Bounds,
    calories: Option[Int] = None,
    servingSize: Option[String] = None,
    servingUnit: Option[String] = None
)

/** State for MyFitnessPal agent
  *
  * @param emulatorRunning
  *   Whether emulator is running
  * @param emulatorDeviceId
  *   Device ID if emulator is running
  * @param emulatorName
  *   AVD name if known
  * @param myFitnessPalOpen
  *   Whether MyFitnessPal app is open
  * @param onFoodSearchScreen
  *   Whether on the food search screen
  * @param currentMeal
  *   Currently selected meal type
  * @param currentSearchQuery
  *   Current search query
  * @param searchResults
  *   List of food search results
  * @param selectedFood
  *   Currently selected food item
  * @param messages
  *   Conversation history (newest first for efficient prepending)
  */
case class MyFitnessPalAgentState(
    emulatorRunning: Boolean = false,
    emulatorDeviceId: Option[String] = None,
    emulatorName: Option[String] = None,
    myFitnessPalOpen: Boolean = false,
    onFoodSearchScreen: Boolean = false,
    currentMeal: Option[MealType] = None,
    currentSearchQuery: Option[String] = None,
    searchResults: List[FoodSearchResult] = List.empty,
    selectedFood: Option[FoodSearchResult] = None,
    messages: List[Message] = List.empty
) extends GraphState:
  
  /** Check if the last message contains tool calls */
  def hasToolCalls: Boolean = messages.headOption match
    case Some(Message.Assistant(AssistantContent.ToolCalls(_))) => true
    case _                                                      => false

  /** Get tool calls from the last assistant message */
  def getToolCalls: List[ToolCall] = messages.headOption match
    case Some(Message.Assistant(AssistantContent.ToolCalls(calls))) =>
      calls.toList
    case _ => List.empty
