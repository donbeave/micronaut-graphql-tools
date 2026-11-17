package example.graphql.type

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class AddBookPayload(val error: Error? = null)
