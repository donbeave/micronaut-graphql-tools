package example

import jakarta.inject.Singleton

@Singleton
class Query : BaseQuery() {

    fun hello(suffix: String): String = "derived " + suffix

}
