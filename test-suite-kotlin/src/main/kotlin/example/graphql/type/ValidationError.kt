package example.graphql.type

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class ValidationError(val message: String, val code: ValidationErrorCode) : Error
