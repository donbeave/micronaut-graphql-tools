package example.model

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Book {

    String id
    String title

}
