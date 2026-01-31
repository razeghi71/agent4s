# agent4s-common-tools

Pre-built, production-ready tools for agent4s agents.

## Installation

```scala
libraryDependencies += "no.marz" %% "agent4s-common-tools" % "0.1.0-SNAPSHOT"
```

## Available Tools

### WebSearchTool

Search the internet for current, real-time information using any LLM provider that supports citations.

**Key Feature:** Generic over providers - works with any `LLMProvider` whose `Response` type includes `HasCitations`.

**Example:**

```scala
import no.marz.agent4s.tools.WebSearchTool
import no.marz.agent4s.llm.provider.perplexity.PerplexityProvider

// Create provider
PerplexityProvider.resourceFromEnv[IO].use { perplexityProvider =>
  // Create web search tool - type inference handles everything!
  val webSearchTool = WebSearchTool(perplexityProvider, "sonar")
  
  // Register with tool registry
  given toolRegistry: ToolRegistry[IO] = 
    ToolRegistry.empty[IO].register(webSearchTool)
  
  // Use in your agent...
}
```

**Input:**
```scala
case class WebSearchInput(query: String)
```

**Output:**
```scala
case class SearchSource(
  url: String, 
  title: Option[String]
)

case class WebSearchOutput(
  answer: String,          // Synthesized answer from sources
  sources: List[SearchSource]  // Source URLs with citations
)
```

**Type Safety:** The tool uses refinement types to constrain the provider's `Response` type to `ChatCompletionResponse & HasCitations`. The factory method provides clean instantiation with full type inference while maintaining compile-time safety.

## Compatible Providers

Any provider implementing `LLMProvider[F]` with `type Response = ChatCompletionResponse & HasCitations` works with WebSearchTool:

- ✅ **PerplexityProvider** - Built-in support with Sonar model
- ✅ **Your custom provider** - Just implement the intersection type
