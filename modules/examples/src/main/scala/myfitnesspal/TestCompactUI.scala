package myfitnesspal

import myfitnesspal.tools.{UIParserTool, ParseUIInput}
import myfitnesspal.model.UIElement
import cats.effect.{IO, IOApp}
import cats.effect.unsafe.implicits.global
import scala.io.Source

/** Test the compact UI representation to compare token usage */
@main def testCompactUI(): Unit =
  val xmlContent = Source.fromFile("window_dump.xml").mkString
  
  println("=== UI Dump Token Comparison ===\n")
  
  // Original XML size
  val xmlTokens = xmlContent.split("\\s+").length
  val xmlChars = xmlContent.length
  println(s"Original XML:")
  println(s"  - Characters: $xmlChars")
  println(s"  - Estimated tokens: $xmlTokens")
  println()
  
  // Parse and get compact representation
  val parser = new UIParserTool[IO]
  val result = parser.execute(ParseUIInput(xmlContent)).unsafeRunSync()
  
  println(s"Parsed UI Tree:")
  println(s"  - Total elements: ${result.flatList.size}")
  println()
  
  // Compact representation
  val compactText = UIElement.toCompactText(result.root)
  val compactTokens = UIElement.estimateTokens(result.root)
  val compactChars = compactText.length
  
  println(s"Compact Representation:")
  println(s"  - Actionable elements: ${UIElement.findActionable(result.root).size}")
  println(s"  - Characters: $compactChars")
  println(s"  - Estimated tokens: $compactTokens")
  println()
  
  val reduction = ((xmlTokens - compactTokens).toDouble / xmlTokens * 100).toInt
  println(s"Token Reduction: ${reduction}%")
  println()
  
  println("=== Compact Output ===")
  println(compactText)
