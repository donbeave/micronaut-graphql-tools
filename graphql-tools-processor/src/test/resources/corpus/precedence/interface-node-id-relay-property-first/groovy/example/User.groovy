package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class User implements Node {

    Long id = 7L
    String name = 'ann'

}
