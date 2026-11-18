package example

class Query : BaseQuery() {

    fun hello(suffix: String): String = "derived " + suffix

}
