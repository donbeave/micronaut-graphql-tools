package example

import io.micronaut.core.annotation.Creator
import io.micronaut.graphql.tools.annotation.GraphQLInput

@GraphQLInput
class Filter private constructor(val text: String) {

    companion object {
        @Creator
        @JvmStatic
        fun of(text: String): Filter = Filter(text)
    }

}
