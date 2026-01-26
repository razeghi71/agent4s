import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import no.marz.agent4s.llm.model.*
import no.marz.agent4s.llm.provider.openai.{OpenAIProvider, OpenAIConfig}

object ChatExample extends IOApp.Simple:
  def run: IO[Unit] =
    OpenAIProvider.resourceFromEnv[IO].use { provider =>
      val request = ChatCompletionRequest(
        model = "gpt-4o-mini",
        messages = Seq(
          Message.System("You are a helpful assistant."),
          Message.User("What is the capital of France?")
        ),
        temperature = Some(0.7)
      )

      for
        _ <- IO.println("Sending request to OpenAI...")
        response <- provider.chatCompletion(request)
        _ <- IO.println(s"\nResponse: ${response.choices.head.message}")
        _ <- response.usage.traverse { usage =>
          IO.println(
            s"Tokens used: ${usage.totalTokens} (prompt: ${usage.promptTokens}, completion: ${usage.completionTokens})"
          )
        }
      yield ()
    }
