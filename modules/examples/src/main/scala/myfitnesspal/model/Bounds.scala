package myfitnesspal.model

/** Represents rectangular bounds of a UI element
  *
  * @param x1
  *   Left coordinate
  * @param y1
  *   Top coordinate
  * @param x2
  *   Right coordinate
  * @param y2
  *   Bottom coordinate
  */
case class Bounds(x1: Int, y1: Int, x2: Int, y2: Int)

object Bounds:
  /** Parse bounds from Android UI dump format: "[x1,y1][x2,y2]"
    *
    * Example: "[0,0][1080,2400]" → Bounds(0, 0, 1080, 2400)
    */
  def parse(s: String): Option[Bounds] =
    val pattern = """\[(\d+),(\d+)\]\[(\d+),(\d+)\]""".r
    s match
      case pattern(x1, y1, x2, y2) =>
        Some(Bounds(x1.toInt, y1.toInt, x2.toInt, y2.toInt))
      case _ => None
