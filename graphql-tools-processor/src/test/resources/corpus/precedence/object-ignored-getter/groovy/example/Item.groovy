package example

import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Item {

    String name = 'pen'

    @GraphQLIgnore
    String label = 'own'

}
