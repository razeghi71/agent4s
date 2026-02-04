# agent4s

Scala 3 toolkit for building AI agents. Unified LLM providers (Claude, OpenAI, Gemini, DeepSeek, Perplexity) with type-safe tool calling and graph-based workflow orchestration. Built on cats-effect, fs2, and http4s.

## Modules

| Module | Description |
|--------|-------------|
| `llm` | Multi-provider LLM abstraction with tool registry and JSON schema derivation |
| `graph` | State machine workflow orchestration with conditional routing |
| `examples` | Example agents (MyFitnessPal Android automation) |

## Quick Start

Add to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "no.marz" %% "agent4s-llm" % "0.1.0",
  "no.marz" %% "agent4s-graph" % "0.1.0"
)
```

## LLM Module

### Supported Providers

| Provider | Models | Env Variable |
|----------|--------|--------------|
| Claude (Anthropic) | claude-sonnet-4-5, claude-opus-4 | `ANTHROPIC_API_KEY` |
| OpenAI | gpt-4o, gpt-4, o3, o4-mini | `OPENAI_API_KEY` |
| Gemini (Google) | gemini-pro, gemini-1.5-pro | `GOOGLE_API_KEY` |
| DeepSeek | deepseek-chat, deepseek-reasoner | `DEEPSEEK_API_KEY` |
| Perplexity | sonar, sonar-pro | `PERPLEXITY_API_KEY` |

### Basic Usage

```scala
import cats.effect.{IO, IOApp}
import no.marz.agent4s.llm.provider.claude.ClaudeProvider
import no.marz.agent4s.llm.model.*

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    ClaudeProvider.resourceFromEnv[IO].use { provider =>
      val request = ChatCompletionRequest(
        model = "claude-sonnet-4-5-20250514",
        messages = List(Message.User("What is 2 + 2?"))
      )

      provider.chatCompletion(request).flatMap { response =>
        IO.println(response.choices.head.message)
      }
    }
```

### Tool Calling

Define tools with automatic JSON schema derivation:

```scala
import no.marz.agent4s.llm.model.Tool
import no.marz.agent4s.llm.ToolRegistry
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description
import io.circe.generic.auto.given

case class WeatherInput(
  @description("City name") city: String,
  @description("Temperature unit") unit: String = "celsius"
)

case class WeatherOutput(temperature: Double, condition: String)

class WeatherTool[F[_]: Async] extends Tool[F, WeatherInput, WeatherOutput]:
  def name = "get_weather"
  def description = "Get current weather for a city"
  def execute(input: WeatherInput): F[WeatherOutput] = ???

// Register tools
val registry = ToolRegistry.empty[IO]
  .register(new WeatherTool[IO])

// Use with LLM
val request = ChatCompletionRequest(
  model = "claude-sonnet-4-5-20250514",
  messages = List(Message.User("What's the weather in Tokyo?")),
  tools = registry.getSchemas
)
```

## Graph Module

Build state machine workflows with conditional routing:

```scala
import cats.effect.IO
import no.marz.agent4s.graph.*

// Define state
case class AgentState(
  messages: List[String] = Nil,
  done: Boolean = false
) extends GraphState

// Define nodes
object ProcessNode extends GraphNode[IO, AgentState]:
  def execute(state: AgentState): IO[AgentState] =
    IO.pure(state.copy(messages = "Processed" :: state.messages))

object CheckNode extends GraphNode[IO, AgentState]:
  def execute(state: AgentState): IO[AgentState] =
    IO.pure(state.copy(done = state.messages.size >= 3))

// Build graph
val graph = GraphBuilder[IO, AgentState]()
  .addNode(ProcessNode)
  .addNode(CheckNode)
  .connect(ProcessNode).to(CheckNode)
  .connect(CheckNode).when(_.done).toTerminal()
  .connect(CheckNode).otherwise.to(ProcessNode)
  .startFrom(ProcessNode)
  .build()

// Execute
val executor = new GraphExecutor[IO]()
executor.run(graph, AgentState())
  .compile
  .lastOrError
  .flatMap(state => IO.println(s"Final: $state"))
```

## ReAct Agent Pattern

Combine LLM and Graph for agentic loops:

```scala
class ChatNode(provider: LLMProvider[IO], registry: ToolRegistry[IO])
    extends GraphNode[IO, AgentState]:
  def execute(state: AgentState): IO[AgentState] =
    val request = ChatCompletionRequest(
      model = "claude-sonnet-4-5-20250514",
      messages = state.messages,
      tools = registry.getSchemas
    )
    provider.chatCompletion(request).map { response =>
      state.copy(messages = state.messages :+ response.choices.head.message)
    }

class ToolNode(registry: ToolRegistry[IO])
    extends GraphNode[IO, AgentState]:
  def execute(state: AgentState): IO[AgentState] =
    state.getToolCalls.traverse(_.execute()).map { results =>
      state.copy(messages = state.messages ++ results)
    }

// Graph: ChatNode <-> ToolNode loop until no tool calls
val graph = GraphBuilder[IO, AgentState]()
  .addNode(chatNode)
  .addNode(toolNode)
  .connect(chatNode).when(_.hasToolCalls).to(toolNode)
  .connect(chatNode).otherwise.toTerminal()
  .connect(toolNode).to(chatNode)
  .startFrom(chatNode)
  .build()
```

## Examples

See [`modules/examples`](modules/examples/src/main/scala) for complete examples:

- **MyFitnessPal Agent**: Android automation agent that logs food via ADB

```bash
# Set required env vars
export DEEPSEEK_API_KEY="your-key"
export ANDROID_HOME="/path/to/android/sdk"
export AVD_NAME="Pixel_6_API_36"

# Run
sbt "examples/runMain myfitnesspal.myFitnessPalAgent"
```

## Architecture

```
agent4s/
├── modules/
│   ├── llm/           # LLM providers, tool registry, models
│   │   └── provider/
│   │       ├── claude/
│   │       ├── openai/
│   │       ├── gemini/
│   │       ├── deepseek/
│   │       └── perplexity/
│   ├── graph/         # Workflow orchestration
│   └── examples/      # Example agents
```

## Requirements

- Scala 3.3+
- JDK 11+

## Dependencies

- cats-effect 3.x
- fs2 3.x
- http4s 0.23.x
- circe 0.14.x
