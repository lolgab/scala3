package dotty.tools.dotc.util

/** A hash set that allows some privileged protected access to its internals
 *  @param  initialCapacity  Indicates the initial number of slots in the hash table.
 *                           The actual number of slots is always a power of 2, so the
 *                           initial size of the table will be the smallest power of two
 *                           that is equal or greater than the given `initialCapacity`.
 *  @param  loadFactor       The maximum fraction of used elements relative to capacity.
 *                           The hash table will be re-sized once the number of elements exceeds
 *                           the current size of the hash table multiplied by loadFactor.
 *  With the defaults given, the first resize of the table happens once the number of elements
 *  grows beyond 16.
 */
class HashSet[T >: Null <: AnyRef](initialCapacity: Int = 16, loadFactor: Float = 0.25f) extends MutableSet[T] {
  private var used: Int = _
  private var limit: Int = _
  private var table: Array[AnyRef] = _

  private def roundToPower(n: Int) =
    if Integer.bitCount(n) == 1 then n
    else
      def recur(n: Int): Int =
        if n == 1 then 2
        else recur(n >>> 1) << 1
      recur(n)

  protected def isEqual(x: T, y: T): Boolean = x.equals(y)

  // Counters for Stats
  var accesses: Int = 0
  var misses: Int = 0

  clear()

  /** The number of elements in the set */
  def size: Int = used

  private def allocate(size: Int) = {
    table = new Array[AnyRef](size)
    limit = (size * loadFactor).toInt
  }

  /** Remove all elements from this set and set back to initial configuration */
  def clear(): Unit = {
    used = 0
    allocate(roundToPower(initialCapacity))
  }

  /** Turn hashcode `x` into a table index */
  private def index(x: Int): Int = x & (table.length - 1)

  /** Hashcode, can be overridden */
  def hash(x: T): Int = x.hashCode

  private def entryAt(idx: Int) = table(idx).asInstanceOf[T]

  /** Find entry such that `isEqual(x, entry)`. If it exists, return it.
   *  If not, enter `x` in set and return `x`.
   */
  def findEntryOrUpdate(x: T): T = {
    if (Stats.enabled) accesses += 1
    var h = index(hash(x))
    var entry = entryAt(h)
    while (entry ne null) {
      if (isEqual(x, entry)) return entry
      if (Stats.enabled) misses += 1
      h = index(h + 1)
      entry = entryAt(h)
    }
    addEntryAt(h, x)
  }

  /** Add entry at `x` at index `idx` */
  private def addEntryAt(idx: Int, x: T) = {
    table(idx) = x
    used += 1
    if (used > limit) growTable()
    x
  }

  /** The entry in the set such that `isEqual(x, entry)`, or else `null`. */
  def lookup(x: T): T = {
    if (Stats.enabled) accesses += 1
    var h = index(hash(x))
    var entry = entryAt(h)
    while ((entry ne null) && !isEqual(x, entry)) {
      if (Stats.enabled) misses += 1
      h = index(h + 1)
      entry = entryAt(h)
    }
    entry.asInstanceOf[T]
  }

  private var rover: Int = -1

  /** Privileged access: Find first entry with given hashcode */
  protected def findEntryByHash(hashCode: Int): T = {
    rover = index(hashCode)
    nextEntryByHash(hashCode)
  }

  /** Privileged access: Find next entry with given hashcode. Needs to immediately
   *  follow a `findEntryByhash` or `nextEntryByHash` operation.
   */
  protected def nextEntryByHash(hashCode: Int): T = {
    if (Stats.enabled) accesses += 1
    var entry = table(rover)
    while (entry ne null) {
      rover = index(rover + 1)
      if (hash(entry.asInstanceOf[T]) == hashCode) return entry.asInstanceOf[T]
      if (Stats.enabled) misses += 1
      entry = table(rover)
    }
    null
  }

  /** Privileged access: Add entry `x` at the last position where an unsuccsessful
   *  `findEntryByHash` or `nextEntryByhash` operation returned. Needs to immediately
   *  follow a `findEntryByhash` or `nextEntryByHash` operation that was unsuccessful,
   *  i.e. that returned `null`.
   */
  protected def addEntryAfterScan(x: T): T = addEntryAt(rover, x)

  private def addOldEntry(x: T): Unit = {
    var h = index(hash(x))
    var entry = entryAt(h)
    while (entry ne null) {
      h = index(h + 1)
      entry = entryAt(h)
    }
    table(h) = x
  }

  private def growTable(): Unit = {
    val oldtable = table
    allocate(table.length * 2)
    var i = 0
    while (i < oldtable.length) {
      val entry = oldtable(i)
      if (entry ne null) addOldEntry(entry.asInstanceOf[T])
      i += 1
    }
  }

  /** Add entry `x` to set */
  def += (x: T): Unit = {
    if (Stats.enabled) accesses += 1
    var h = index(hash(x))
    var entry = entryAt(h)
    while (entry ne null) {
      if (isEqual(x, entry)) return
      if (Stats.enabled) misses += 1
      h = index(h + 1)
      entry = entryAt(h)
    }
    table(h) = x
    used += 1
    if (used > (table.length >> 2)) growTable()
  }

  def -= (x: T): Unit =
    if (Stats.enabled) accesses += 1
    var h = index(hash(x))
    var entry = entryAt(h)
    while entry != null do
      if isEqual(x, entry) then
        var hole = h
        while
          h = index(h + 1)
          entry = entryAt(h)
          entry != null && index(hash(entry)) != h
        do
          table(hole) = entry
          hole = h
        table(hole) = null
        used -= 1
        return
      h = index(h + 1)
      entry = entryAt(h)
  end -=

  /** Add all entries in `xs` to set */
  def ++= (xs: IterableOnce[T]): Unit =
    xs.iterator.foreach(this += _)

  /** The iterator of all elements in the set */
  def iterator: Iterator[T] = new Iterator[T] {
    private var i = 0
    def hasNext: Boolean = {
      while (i < table.length && (table(i) eq null)) i += 1
      i < table.length
    }
    def next(): T =
      if (hasNext) { i += 1; table(i - 1).asInstanceOf[T] }
      else null
  }

  override def toString(): String = "HashSet(%d / %d)".format(used, table.length)
}
