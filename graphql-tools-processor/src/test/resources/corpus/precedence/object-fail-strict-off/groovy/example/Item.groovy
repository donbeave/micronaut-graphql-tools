package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Item {

    String name = 'pen'

    String label = 'own'

}
