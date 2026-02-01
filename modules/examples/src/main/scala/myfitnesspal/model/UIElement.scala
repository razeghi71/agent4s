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

object UIElement:
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
