package myfitnesspal.model

/** Represents a UI element from Android UI hierarchy dump
  *
  * @param index
  *   Element index in tree (e.g., "0.2.1")
  * @param className
  *   Android widget class name (e.g., "android.widget.TextView")
  * @param text
  *   Visible text content
  * @param contentDesc
  *   Content description for accessibility
  * @param resourceId
  *   Android resource ID (e.g., "com.myfitnesspal:id/search_button")
  * @param bounds
  *   Screen coordinates
  * @param clickable
  *   Whether element is clickable
  * @param scrollable
  *   Whether element is scrollable
  * @param focusable
  *   Whether element can receive focus
  * @param enabled
  *   Whether element is enabled
  * @param children
  *   Child elements in hierarchy
  */
case class UIElement(
    index: String,
    className: String,
    text: Option[String],
    contentDesc: Option[String],
    resourceId: Option[String],
    bounds: Bounds,
    clickable: Boolean,
    scrollable: Boolean,
    focusable: Boolean,
    enabled: Boolean,
    children: List[UIElement] = List.empty
):
  /** Check if element contains text (case-insensitive) */
  def containsText(query: String): Boolean =
    text.exists(_.toLowerCase.contains(query.toLowerCase))

  /** Check if content description contains text (case-insensitive) */
  def containsContentDesc(query: String): Boolean =
    contentDesc.exists(_.toLowerCase.contains(query.toLowerCase))

  /** Check if resource ID matches */
  def hasResourceId(id: String): Boolean =
    resourceId.contains(id)

  /** Get all descendants (flattened tree) */
  def descendants: List[UIElement] =
    this :: children.flatMap(_.descendants)

  /** Generate a compact single-line representation for LLM consumption
    * Format: [id] label (shortClass) [actions] @ bounds
    */
  def toCompactLine(id: Int): String =
    val label = text
      .orElse(contentDesc)
      .orElse(resourceId.map(_.split("/").lastOption.getOrElse("")))
      .getOrElse("(no label)")
    val shortClass = className.split("\\.").lastOption.getOrElse(className)
    val actions = List(
      if clickable then Some("click") else None,
      if scrollable then Some("scroll") else None,
      if focusable then Some("focus") else None
    ).flatten
    val actionsStr = if actions.nonEmpty then s" [${actions.mkString(",")}]" else ""
    val boundsStr = s"[${bounds.x1},${bounds.y1}][${bounds.x2},${bounds.y2}]"
    
    s"[$id] $label ($shortClass)$actionsStr @ $boundsStr"

object UIElement:
  /** Parse an XML node into a UIElement */
  def fromXmlNode(node: scala.xml.Node, indexPath: String): UIElement =
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

    val childNodes = node \ "node"
    val children = childNodes.zipWithIndex.map { case (childNode, idx) =>
      fromXmlNode(childNode, s"$indexPath.$idx")
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

  /** Find all elements matching a predicate in tree */
  def findAll(
      root: UIElement,
      predicate: UIElement => Boolean
  ): List[UIElement] =
    root.descendants.filter(predicate)

  /** Find first element matching predicate in tree */
  def findFirst(
      root: UIElement,
      predicate: UIElement => Boolean
  ): Option[UIElement] =
    root.descendants.find(predicate)

  /** Find all elements by text */
  def findByText(root: UIElement, text: String): List[UIElement] =
    findAll(root, _.containsText(text))

  /** Find all elements by content description (exact match, case-insensitive) */
  def findByContentDesc(root: UIElement, desc: String): Option[UIElement] =
    findFirst(root, elem => elem.contentDesc.exists(_.equalsIgnoreCase(desc)))

  /** Find all elements with resource ID */
  def findByResourceId(root: UIElement, id: String): List[UIElement] =
    findAll(root, _.hasResourceId(id))

  /** Find all elements by class name */
  def findByClassName(root: UIElement, className: String): List[UIElement] =
    findAll(root, _.className == className)

  /** Find all text-containing elements */
  def findTextContaining(root: UIElement, query: String): List[UIElement] =
    findAll(root, elem => elem.text.exists(_.toLowerCase.contains(query.toLowerCase)))

  /** Find all clickable elements */
  def findClickable(root: UIElement): List[UIElement] =
    findAll(root, _.clickable)

  /** Find all actionable or informative elements (for LLM consumption)
    * 
    * Filters to only elements that are:
    * - Clickable, scrollable, or focusable (actionable)
    * - Have text or content description (informative)
    * - Are enabled
    */
  def findActionable(root: UIElement): List[UIElement] =
    findAll(root, elem => 
      elem.enabled && (
        elem.clickable || 
        elem.scrollable || 
        elem.focusable ||
        elem.text.isDefined || 
        elem.contentDesc.isDefined
      )
    )

  /** Generate a compact text representation for LLM consumption
    * 
    * This dramatically reduces tokens by:
    * - Only including actionable/informative elements
    * - Using short class names
    * - Using concise format: [id] label (type) [actions] @ bounds
    * 
    * @param root The root UIElement
    * @return Compact multi-line string representation
    */
  def toCompactText(root: UIElement): String =
    val actionable = findActionable(root)
    val lines = actionable.zipWithIndex.map { case (elem, idx) =>
      elem.toCompactLine(idx)
    }
    s"Elements: ${actionable.size}\n" + lines.mkString("\n")

  /** Estimate token count for compact representation */
  def estimateTokens(root: UIElement): Int =
    toCompactText(root).split("\\s+").length
