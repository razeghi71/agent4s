package myfitnesspal.tools

import cats.effect.kernel.Async
import no.marz.agent4s.llm.model.Tool
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given
import com.melvinlow.json.schema.annotation.description

case class GetUserInputInput(
    @description("The question or prompt to show the user")
    prompt: String
)

case class GetUserInputOutput(
    userResponse: String
)

object GetUserInputInput:
  given Decoder[GetUserInputInput] = deriveDecoder[GetUserInputInput]
  given Encoder[GetUserInputInput] = deriveEncoder[GetUserInputInput]

object GetUserInputOutput:
  given Encoder[GetUserInputOutput] = deriveEncoder[GetUserInputOutput]

/** Get input from the user
  *
  * Use this tool when you need to ask the user a question or get confirmation.
  * Examples:
  *   - Confirming corrected food list
  *   - Asking for meal type (Breakfast/Lunch/Dinner/Snacks)
  *   - Verifying if calories look correct
  *   - Confirming final summary
  */
class GetUserInputTool[F[_]: Async]
    extends Tool[F, GetUserInputInput, GetUserInputOutput]:

  def name: String = "get_user_input"

  def description: String =
    "Ask the user a question and get their response. Use this for confirmations, clarifications, or when you need information from the user."

  def execute(input: GetUserInputInput): F[GetUserInputOutput] =
    Async[F].blocking {
      println(s"\n${input.prompt}")
      print("> ")
      val userResponse = scala.io.StdIn.readLine()
      GetUserInputOutput(userResponse)
    }
