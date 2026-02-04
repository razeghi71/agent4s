package myfitnesspal.model

import no.marz.agent4s.graph.GraphState
import no.marz.agent4s.llm.model.{Message, AssistantContent, ToolCall}

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
  * @param messages
  *   Conversation history (newest first for efficient prepending)
  */
case class MyFitnessPalAgentState(
    emulatorRunning: Boolean = false,
    emulatorDeviceId: Option[String] = None,
    emulatorName: Option[String] = None,
    myFitnessPalOpen: Boolean = false,
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
