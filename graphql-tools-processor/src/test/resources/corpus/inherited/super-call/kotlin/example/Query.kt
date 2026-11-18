package example

class Query : BaseQuery() {

    override fun hello(): String = super.hello() + " derived"

}
