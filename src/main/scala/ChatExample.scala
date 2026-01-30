import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import no.marz.agent4s.llm.model.*
import no.marz.agent4s.llm.provider.openai.{OpenAIProvider, OpenAIConfig}
import no.marz.agent4s.llm.ToolRegistry
import no.marz.agent4s.llm.ToolRegistry.execute
import io.circe.Json
import io.circe.syntax.*
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import io.circe.generic.auto.given

case class WeatherInput(location: String, unit: Option[String])
case class WeatherOutput(temperature: Double, conditions: String, unit: String)

object WeatherTool extends Tool[IO, WeatherInput, WeatherOutput]:
  def name = "get_weather"
  def description = "Get the current weather for a location"

  def execute(input: WeatherInput): IO[WeatherOutput] =
    IO.pure(
      WeatherOutput(
        temperature = 72.0,
        conditions = "Sunny",
        unit = input.unit.getOrElse("fahrenheit")
      )
    )

object ChatExample extends IOApp.Simple:
  def run: IO[Unit] =
    val registry = ToolRegistry
      .empty[IO]
      .register(WeatherTool)

    OpenAIProvider.resourceFromEnv[IO].use { provider =>
      for
        _ <- IO.println("=" * 60)
        _ <- IO.println("TEST 1: Basic chat without tools")
        _ <- IO.println("=" * 60)
        _ <- basicChatExample(provider)
        _ <- IO.println("\n")
        _ <- IO.println("=" * 60)
        _ <- IO.println("TEST 2: Chat with tool calling")
        _ <- IO.println("=" * 60)
        _ <- toolCallingExample(provider, registry)
      yield ()
    }

  def basicChatExample(
      provider: no.marz.agent4s.llm.LLMProvider[IO]
  ): IO[Unit] =
    val request = ChatCompletionRequest(
      model = "gpt-4o-mini",
      messages = Seq(
        Message.System("You are a helpful assistant."),
        Message.User("What is the capital of France?")
      ),
      temperature = Some(0.7)
    )

    for
      _ <- IO.println("Sending basic request to OpenAI...")
      response <- provider.chatCompletion(request)
      _ <- IO.println(s"\nResponse: ${response.choices.head.message}")
      _ <- response.usage.traverse { usage =>
        IO.println(
          s"Tokens used: ${usage.totalTokens} (prompt: ${usage.promptTokens}, completion: ${usage.completionTokens})"
        )
      }
    yield ()

  def toolCallingExample(
      provider: no.marz.agent4s.llm.LLMProvider[IO],
      registry: ToolRegistry[IO]
  ): IO[Unit] =
    given ToolRegistry[IO] = registry

    val request = ChatCompletionRequest(
      model = "gpt-5-nano",
      messages = Seq(
        Message.System("You are a helpful weather assistant."),
        Message.User("What's the weather like in San Francisco?")
      ),
      tools = registry.getSchemas,
      temperature = Some(0.7)
    )

    for
      _ <- IO.println("Sending request with tools to OpenAI...")
      _ <- IO.println(
        s"Tools available: ${request.tools.map(_.name).mkString(", ")}"
      )
      response <- provider.chatCompletion(request)
      _ <- IO.println(s"\nFinish reason: ${response.choices.head.finishReason}")

      _ <- response.choices.head.message match
        case Message.Assistant(AssistantContent.Text(text)) =>
          IO.println(s"Text response: $text")
        case Message.Assistant(AssistantContent.ToolCalls(calls)) =>
          IO.println(s"Tool calls requested (${calls.length}):") *>
            calls.traverse_ { call =>
              IO.println(s"  - Tool: ${call.name}") *>
                IO.println(s"    ID: ${call.id}") *>
                IO.println(s"    Arguments: ${call.arguments.spaces2}") *>
                IO.println(s"\nExecuting tool call...") *>
                call.execute[IO]().flatMap { toolMessage =>
                  IO.println(s"Tool result: ${toolMessage.content}")
                }
            }
        case other =>
          IO.println(s"Unexpected message type: $other")

      _ <- response.usage.traverse { usage =>
        IO.println(
          s"\nTokens used: ${usage.totalTokens} (prompt: ${usage.promptTokens}, completion: ${usage.completionTokens})"
        )
      }
    yield ()
