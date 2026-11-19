package example

import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class BookFilter(val title: String?, val genres: List<Genre>?, val locale: String?) {

    val empty: Boolean
        get() = title == null

}
