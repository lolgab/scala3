//> using options -Wshadow:extension-shadow

object foo:
  opaque type Foo = Array[String]
  def create(array: Array[String]): Foo = array
  extension (headers: Foo)
    def length: Int = 10
    def apply(i: Int): String = "x"
    def own: Int = 1
    def printLength(): Unit =
      println(s"length = $length") // warn
    def first: String = apply(0) // warn
    def sum: Int = own + length // warn
    def explicitMember: Int = headers.length // ok, selects the member explicitly
    def explicitExtension: Int = this.length(headers) // ok, calls the extension method

class C:
  def m: Int = 1

object bar:
  extension (c: C)
    def m: Int = 2 // warn
    def n: Int = m // warn

// Extension methods returning context functions must not be confused with members
object ctx:
  class Ref
  extension (ref: Ref)
    def objekt: String ?=> Int = 1
    def use(using String): Int = objekt
