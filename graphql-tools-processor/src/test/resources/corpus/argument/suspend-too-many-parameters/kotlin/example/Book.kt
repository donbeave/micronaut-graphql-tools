package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
data class Book(val id: String, val title: String)
