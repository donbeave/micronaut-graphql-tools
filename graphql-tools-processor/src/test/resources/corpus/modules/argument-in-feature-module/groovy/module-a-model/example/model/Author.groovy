package example.model

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Author {

    String id
    String name

}
