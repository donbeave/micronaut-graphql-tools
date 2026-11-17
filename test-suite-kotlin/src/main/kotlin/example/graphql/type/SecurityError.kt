package example.graphql.type

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class SecurityError(val message: String, val code: SecurityErrorCode) : Error
