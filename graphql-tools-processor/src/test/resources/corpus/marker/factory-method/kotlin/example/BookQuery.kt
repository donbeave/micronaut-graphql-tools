package example

open class BookQuery : BookQueryContract {

    override fun book(id: String): Book = Book()

    open fun describe(): String = "not a field"

}
