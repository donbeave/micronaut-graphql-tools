package example

import jakarta.inject.Singleton

@Singleton
class Query : BaseQuery() {

    override fun hello(): String = "derived"

}
