package myfitnesspal.tools

import cats.effect.kernel.Async
import cats.syntax.all.*
import myfitnesspal.model.{UIElement, Bounds}
import scala.xml.*
import no.marz.agent4s.llm.model.Tool
import io.circe.{Decoder, Encoder, Json}
import com.melvinlow.json.schema.JsonSchemaEncoder
import com.melvinlow.json.schema.generic.auto.given

/** Input for UIParserTool */
case class ParseUIInput(xmlContent: String)

object ParseUIInput:
  given Encoder[ParseUIInput] = Encoder.instance(input =>
    Json.obj("xmlContent" -> Json.fromString(input.xmlContent.take(100) + "..."))
  )
  given Decoder[ParseUIInput] = Decoder.instance(cursor =>
    cursor.get[String]("xmlContent").map(ParseUIInput(_))
  )

/** Output from UIParserTool */
case class ParseUIOutput(
    root: UIElement,
    flatList: List[UIElement]
)

object ParseUIOutput:
  given Encoder[ParseUIOutput] = Encoder.instance(output =>
    Json.obj(
      "elementCount" -> Json.fromInt(output.flatList.size),
      "rootClassName" -> Json.fromString(output.root.className)
    )
  )

/** Parse Android UI XML dump into structured elements
  *
  * Parses the XML output from `adb shell uiautomator dump` into a tree of
  * UIElement objects.
  *
  * Example XML structure:
  * {{{
  * <hierarchy rotation="0">
  *   <node index="0" text="" resource-id="" class="android.widget.FrameLayout"
  *         bounds="[0,0][1080,2400]" clickable="false" ...>
  *     <node index="0" text="Search" class="android.widget.EditText"
  *           bounds="[50,100][1030,180]" clickable="true" .../>
  *   </node>
  * </hierarchy>
  * }}}
  */
class UIParserTool[F[_]: Async] extends Tool[F, ParseUIInput, ParseUIOutput]:

  def name: String = "ui_parser"

  def description: String =
    "Parse Android UI XML dump into structured elements"

  def execute(input: ParseUIInput): F[ParseUIOutput] =
    Async[F].delay {
      val xml = XML.loadString(input.xmlContent)
      val nodes = xml \\ "node"
      val root = parseNode(nodes.head, "0")
      val flatList = root.descendants
      ParseUIOutput(root, flatList)
    }.handleErrorWith { error =>
      Async[F].raiseError(
        new RuntimeException(s"Failed to parse UI XML: ${error.getMessage}")
      )
    }

  /** Parse a single XML node into UIElement */
  private def parseNode(node: Node, indexPath: String): UIElement =
    val className = (node \@ "class")
    val text = Option(node \@ "text").filter(_.nonEmpty)
    val contentDesc = Option(node \@ "content-desc").filter(_.nonEmpty)
    val resourceId = Option(node \@ "resource-id").filter(_.nonEmpty)
    val boundsStr = node \@ "bounds"
    val bounds = Bounds.parse(boundsStr).getOrElse(Bounds(0, 0, 0, 0))
    val clickable = (node \@ "clickable") == "true"
    val scrollable = (node \@ "scrollable") == "true"
    val focusable = (node \@ "focusable") == "true"
    val enabled = (node \@ "enabled") == "true"

    // Parse children recursively
    val childNodes = node \ "node"
    val children = childNodes.zipWithIndex.map { case (childNode, idx) =>
      parseNode(childNode, s"$indexPath.$idx")
    }.toList

    UIElement(
      index = indexPath,
      className = className,
      text = text,
      contentDesc = contentDesc,
      resourceId = resourceId,
      bounds = bounds,
      clickable = clickable,
      scrollable = scrollable,
      focusable = focusable,
      enabled = enabled,
      children = children
    )
