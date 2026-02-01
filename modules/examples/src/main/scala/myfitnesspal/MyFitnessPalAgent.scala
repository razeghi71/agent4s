package myfitnesspal

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*

import scala.concurrent.duration.*
import no.marz.agent4s.graph.*
import no.marz.agent4s.llm.model.{
  AssistantContent, ChatCompletionRequest, Message
}
import no.marz.agent4s.llm.{LLMProvider, ToolRegistry}
import no.marz.agent4s.llm.ToolRegistry.execute
import no.marz.agent4s.llm.provider.openai.OpenAIProvider
import myfitnesspal.model.MyFitnessPalAgentState
import myfitnesspal.tools.adb.*
import myfitnesspal.tools.{
  EmulatorCheckInput, EmulatorManagerTool, GetUserInputTool, UIParserTool
}
import com.melvinlow.json.schema.generic.auto.given
import io.circe.generic.auto.given

/** Nodes for MyFitnessPal food logging graph */

/** Start node - initializes the workflow */
object StartNode extends GraphNode[IO, MyFitnessPalAgentState]:
  override def execute(state: MyFitnessPalAgentState)
      : IO[MyFitnessPalAgentState] =
    IO {
      println("\n=== MyFitnessPal Food Logger Starting ===\n")
      state
    }

/** Ensure emulator is running */
class EnsureEmulatorNode(using emulatorTool: EmulatorManagerTool[IO])
    extends GraphNode[IO, MyFitnessPalAgentState]:
  override def execute(state: MyFitnessPalAgentState)
      : IO[MyFitnessPalAgentState] =
    if state.emulatorRunning then
      IO {
        println(
          s"Emulator already running: ${state.emulatorDeviceId.getOrElse("unknown")}"
        )
        state
      }
    else
      emulatorTool.execute(EmulatorCheckInput()).map { status =>
        println(
          s"Emulator status: running=${status.running}, device=${status.deviceId}"
        )
        state.copy(
          emulatorRunning = status.running,
          emulatorDeviceId = status.deviceId,
          emulatorName = status.emulatorName
        )
      }

/** Launch MyFitnessPal app - kills first to ensure clean slate */
class LaunchAppNode(using
    killAppTool: KillAppTool[IO],
    launchAppTool: LaunchAppTool[IO]
) extends GraphNode[IO, MyFitnessPalAgentState]:

  private val packageName = "com.myfitnesspal.android"

  override def execute(state: MyFitnessPalAgentState)
      : IO[MyFitnessPalAgentState] =
    if state.myFitnessPalOpen then
      IO(state)
    else
      for
        // Step 1: Kill the app to ensure clean state
        _ <- IO.println("Killing MyFitnessPal to ensure clean state...")
        killResult <- killAppTool.execute(
          KillAppInput(
            packageName = packageName,
            deviceId = state.emulatorDeviceId
          )
        )
        _ <- IO.println(s"Kill result: ${killResult.message}")

        // Step 2: Wait a moment for process to fully terminate
        _ <- IO.sleep(2.seconds)

        // Step 3: Launch the app
        _ <- IO.println("Launching MyFitnessPal...")
        launchResult <- launchAppTool.execute(
          LaunchAppInput(
            packageName = packageName,
            deviceId = state.emulatorDeviceId
          )
        )

        // Step 4: Check if launch was successful
        result <- if launchResult.success then
          // Wait for app to fully launch and load home screen
          IO.sleep(5.seconds) >>
            IO {
              println("MyFitnessPal launched successfully")
              state.copy(myFitnessPalOpen = true)
            }
        else
          IO.raiseError(
            new Exception(s"Failed to launch app: ${launchResult.message}")
          )
      yield result

/** Main reasoning node - uses LLM with tools to interact with app */
class ChatNode(
    llmProvider: LLMProvider[IO],
    toolRegistry: ToolRegistry[IO],
    delayBetweenCalls: FiniteDuration = 2.seconds // Delay to avoid rate limits
) extends GraphNode[IO, MyFitnessPalAgentState]:

  override def execute(state: MyFitnessPalAgentState)
      : IO[MyFitnessPalAgentState] =
    // Add delay before API call to respect rate limits (except for first call)
    val callWithDelay = if state.messages.nonEmpty then
      IO.println(
        s"⏱️  Waiting ${delayBetweenCalls.toSeconds}s to respect rate limits..."
      ) >>
        IO.sleep(delayBetweenCalls) >>
        makeApiCall(state)
    else
      makeApiCall(state)

    callWithDelay

  private def makeApiCall(state: MyFitnessPalAgentState)
      : IO[MyFitnessPalAgentState] =
    // Build messages: system prompt + conversation history (reversed to chronological)
    val messages = Message.System(SystemPrompt.prompt) :: state.messages.reverse

    val request = ChatCompletionRequest(
      model = "gpt-4o",
      messages = messages,
      tools = toolRegistry.getSchemas
    )

    llmProvider.chatCompletion(request).map { response =>
      val assistantMessage = response.choices.headOption
        .map(_.message)
        .getOrElse(Message.Assistant(AssistantContent.Text("No response")))

      // Print assistant response
      assistantMessage match
        case Message.Assistant(AssistantContent.Text(text)) =>
          println(s"\nAssistant: $text")
        case Message.Assistant(AssistantContent.ToolCalls(calls)) =>
          println(s"\nAssistant: [Calling ${calls.size} tools]")
          calls.foreach { call =>
            println(s"  - ${call.name}(${call.arguments.spaces2})")
          }
        case _ => ()

      // Add assistant message to state (prepend for newest-first ordering)
      state.copy(messages = assistantMessage :: state.messages)
    }

/** Execute tool calls made by the LLM */
class ToolNode(using toolRegistry: ToolRegistry[IO])
    extends GraphNode[IO, MyFitnessPalAgentState]:
  override def execute(state: MyFitnessPalAgentState)
      : IO[MyFitnessPalAgentState] =
    val toolCalls = state.getToolCalls

    toolCalls.traverse { toolCall =>
      println(s"\nExecuting tool: ${toolCall.name}")
      toolCall.execute(returnErrorsAsToolMessages = true)
    }.map { toolMessages =>
      // Print tool results
      toolMessages.foreach { msg =>
        println(s"Tool result: ${msg.content.take(200)}${
            if msg.content.length > 200 then "..." else ""
          }")
      }

      // Add all tool result messages to state (reverse to maintain newest-first order)
      state.copy(messages = toolMessages.reverse ::: state.messages)
    }

/** Main application */
@main def myFitnessPalAgent(): Unit =
  val result = OpenAIProvider.resourceFromEnv[IO].use { openAIProvider =>
    // Configure tools
    given tapTool: TapTool[IO] = new TapTool[IO]
    given typeTextTool: TypeTextTool[IO] = new TypeTextTool[IO]
    given swipeTool: SwipeTool[IO] = new SwipeTool[IO]
    given pressKeyTool: PressKeyTool[IO] = new PressKeyTool[IO]
    given launchAppTool: LaunchAppTool[IO] = new LaunchAppTool[IO]
    given killAppTool: KillAppTool[IO] = new KillAppTool[IO]
    given getUITool: GetUITool[IO] = new GetUITool[IO]
    given executeShellTool: ExecuteShellTool[IO] = new ExecuteShellTool[IO]
    given uiParserTool: UIParserTool[IO] = new UIParserTool[IO]
    given getUserInputTool: GetUserInputTool[IO] = new GetUserInputTool[IO]
    given emulatorTool: EmulatorManagerTool[IO] =
      new EmulatorManagerTool[IO]("Pixel_6_API_36")

    // Setup tool registry
    given toolRegistry: ToolRegistry[IO] =
      ToolRegistry
        .empty[IO]
        .register(tapTool)
        .register(typeTextTool)
        .register(swipeTool)
        .register(pressKeyTool)
        .register(getUITool)
        .register(executeShellTool)
        .register(uiParserTool)
        .register(getUserInputTool)

    // Create nodes
    val ensureEmulatorNode = new EnsureEmulatorNode
    val launchAppNode = new LaunchAppNode
    val chatNode =
      new ChatNode(
        openAIProvider,
        toolRegistry,
        delayBetweenCalls = 2.seconds
      )
    val toolNode = new ToolNode

    // Build simplified graph - LLM decides when to ask for user input via tool
    val graph = GraphBuilder[IO, MyFitnessPalAgentState]()
      .addNode(StartNode)
      .addNode(ensureEmulatorNode)
      .addNode(launchAppNode)
      .addNode(chatNode)
      .addNode(toolNode)
      .connect(StartNode).to(ensureEmulatorNode)
      .connect(ensureEmulatorNode).to(launchAppNode)
      .connect(launchAppNode).to(chatNode)
      .connect(chatNode).when(_.hasToolCalls).to(toolNode)
      .connect(chatNode).otherwise.toTerminal()
      .connect(toolNode).to(chatNode)
      .startFrom(StartNode)
      .build()

    println(s"Graph created with ${graph.nodes.size} nodes")

    // Get user input
    println("\n=== MyFitnessPal Food Logger ===")
    println("Please enter the foods you want to log:")
    println(
      "Example: 113g fried minced beef, 135g white rice cooked, 50g greek yogurt"
    )
    print("> ")
    val userInput = Option(scala.io.StdIn.readLine()).getOrElse {
      println("No input provided, using example...")
      "2 eggs, 1 cup oatmeal, 1 banana"
    }
    println(s"Processing: $userInput")

    // Initial state with user message
    val initialState = MyFitnessPalAgentState(
      messages = List(Message.User(userInput))
    )

    // Execute graph
    val executor = new GraphExecutor[IO]()
    executor
      .run(graph, initialState)
      .compile
      .toList
      .flatMap { states =>
        IO {
          println(s"\n=== Execution completed with ${states.size} states ===")
          states.lastOption.foreach { finalState =>
            println("\n=== Final Conversation ===")
            finalState.messages.reverse.foreach {
              case Message.System(_)     => // Don't print system messages
              case Message.User(content) =>
                println(s"\nUser: $content")
              case Message.Assistant(AssistantContent.Text(text)) =>
                println(s"\nAssistant: $text")
              case Message.Assistant(AssistantContent.ToolCalls(calls)) =>
                println(s"\nAssistant: [Called ${calls.size} tools]")
              case Message.Tool(_, name, content) =>
                println(s"\nTool ($name): ${content.take(100)}...")
            }
          }
        }
      }
  }

  result.unsafeRunSync()
