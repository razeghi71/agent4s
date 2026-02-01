import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import no.marz.agent4s.llm.model.*
import no.marz.agent4s.llm.ToolRegistry
import no.marz.agent4s.llm.ToolRegistry.execute
import no.marz.agent4s.llm.provider.claude.ClaudeProvider
import com.melvinlow.json.schema.annotation.description
import com.melvinlow.json.schema.generic.auto.given
import io.circe.generic.auto.given

/** Example demonstrating the Claude (Anthropic) Messages API */

// Define a simple calculator tool
case class ClaudeCalculatorInput(
    @description("Mathematical expression to evaluate, e.g. '2 + 3 * 4'")
    expression: String
)

case class ClaudeCalculatorOutput(
    result: Double,
    expression: String
)

object ClaudeCalculatorTool extends Tool[IO, ClaudeCalculatorInput, ClaudeCalculatorOutput]:
  def name: String = "calculator"
  def description: String = "Evaluates a mathematical expression and returns the result"

  def execute(input: ClaudeCalculatorInput): IO[ClaudeCalculatorOutput] =
    IO {
      val result = evaluateExpression(input.expression)
      ClaudeCalculatorOutput(result, input.expression)
    }

  private def evaluateExpression(expr: String): Double =
    val cleaned = expr.replaceAll("[^0-9+\\-*/.(). ]", "")
    try
      // Use JavaScript engine for evaluation (available in JVM)
      val engine = new javax.script.ScriptEngineManager().getEngineByName("js")
      if engine != null then
        engine.eval(cleaned).toString.toDouble
      else
        cleaned.trim.toDouble
    catch
      case _: Exception => 0.0

// Define a greeting tool  
case class ClaudeGreetingInput(
    @description("Name of the person to greet")
    name: String,
    @description("Language for the greeting (english, spanish, french)")
    language: Option[String]
)

case class ClaudeGreetingOutput(greeting: String)

object ClaudeGreetingTool extends Tool[IO, ClaudeGreetingInput, ClaudeGreetingOutput]:
  def name: String = "greet"
  def description: String = "Generates a personalized greeting in the specified language"

  def execute(input: ClaudeGreetingInput): IO[ClaudeGreetingOutput] =
    IO {
      val greeting = input.language.getOrElse("english").toLowerCase match
        case "spanish" => s"Hola, ${input.name}! Bienvenido!"
        case "french"  => s"Bonjour, ${input.name}! Bienvenue!"
        case _         => s"Hello, ${input.name}! Welcome!"
      ClaudeGreetingOutput(greeting)
    }

@main def claudeApiExample(): Unit =
  println("\n=== Claude (Anthropic) Messages API Example ===\n")

  val program = ClaudeProvider.resourceFromEnv[IO].use { provider =>
    // Setup tool registry
    given toolRegistry: ToolRegistry[IO] = ToolRegistry
      .empty[IO]
      .register(ClaudeCalculatorTool)
      .register(ClaudeGreetingTool)

    // Example 1: Simple chat without tools
    println("--- Example 1: Simple Chat ---")
    val simpleRequest = ChatCompletionRequest(
      model = "claude-sonnet-4-20250514", // Claude Sonnet 4
      messages = Seq(
        Message.System("You are a helpful assistant. Be concise."),
        Message.User("What is the capital of France?")
      )
    )

    for
      response1 <- provider.chatCompletion(simpleRequest)
      _ <- IO {
        response1.choices.headOption.foreach { choice =>
          choice.message match
            case Message.Assistant(AssistantContent.Text(text)) =>
              println(s"Assistant: $text")
            case _ => println("Unexpected response type")
        }
        println(s"Tokens used: ${response1.usage.map(_.totalTokens).getOrElse("N/A")}")
      }

      // Example 2: Chat with tool calling
      _ <- IO.println("\n--- Example 2: Tool Calling ---")
      toolRequest = ChatCompletionRequest(
        model = "claude-sonnet-4-20250514", 
        messages = Seq(
          Message.System("You are a helpful assistant with access to tools. Use them when appropriate."),
          Message.User("Please greet Alice in Spanish, and also calculate 15 * 7 + 23")
        ),
        tools = toolRegistry.getSchemas
      )

      response2 <- provider.chatCompletion(toolRequest)
      _ <- IO {
        response2.choices.headOption.foreach { choice =>
          choice.message match
            case Message.Assistant(AssistantContent.ToolCalls(calls)) =>
              println(s"Assistant wants to call ${calls.size} tool(s):")
              calls.foreach { call =>
                println(s"  - ${call.name}: ${call.arguments.noSpaces}")
              }
            case Message.Assistant(AssistantContent.Text(text)) =>
              println(s"Assistant: $text")
            case _ => println("Unexpected response type")
        }
      }

      // Execute tool calls if any
      toolMessages <- response2.choices.headOption match
        case Some(ChatCompletionChoice(_, Message.Assistant(AssistantContent.ToolCalls(calls)), _)) =>
          calls.toList.traverse { call =>
            println(s"\nExecuting tool: ${call.name}")
            call.execute(returnErrorsAsToolMessages = true)
          }
        case _ => IO.pure(List.empty[Message.Tool])

      _ <- IO {
        if toolMessages.nonEmpty then
          println("\nTool Results:")
          toolMessages.foreach { msg =>
            println(s"  ${msg.toolName}: ${msg.content}")
          }
      }

      // Continue conversation with tool results
      _ <- if toolMessages.nonEmpty then
        IO.println("\n--- Continuing with tool results ---") >>
        provider.chatCompletion(
          ChatCompletionRequest(
            model = "claude-sonnet-4-20250514",
            messages = Seq(
              Message.System("You are a helpful assistant. Summarize the tool results."),
              Message.User("Please greet Alice in Spanish, and also calculate 15 * 7 + 23")
            ) ++ Seq(response2.choices.head.message) ++ toolMessages,
            tools = toolRegistry.getSchemas
          )
        ).flatMap { finalResponse =>
          IO {
            finalResponse.choices.headOption.foreach { choice =>
              choice.message match
                case Message.Assistant(AssistantContent.Text(text)) =>
                  println(s"Final Response: $text")
                case _ => println("Unexpected response type")
            }
          }
        }
      else IO.unit

    yield ()
  }

  program.unsafeRunSync()
  println("\n=== Example Complete ===")
