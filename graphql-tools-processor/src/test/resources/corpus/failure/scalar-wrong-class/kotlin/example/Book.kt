package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Book(val title: Long)
