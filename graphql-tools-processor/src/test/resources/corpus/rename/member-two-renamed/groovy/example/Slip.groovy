package example

import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Slip {

    @GraphQLField("title")
    String first() {
        return 'first'
    }

    @GraphQLField("title")
    String second() {
        return 'second'
    }

}
