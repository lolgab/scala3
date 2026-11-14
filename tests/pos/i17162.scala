//> using options -Werror

// -Wshadow:extension-shadow is opt-in: no warning by default
object foo:
  opaque type Foo = Array[String]
  extension (headers: Foo)
    def length: Int = 10
    def printLength(): Unit = println(length)
