package example.graphql.type

import example.data.User
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class SignInPayload private constructor(val error: Error?, val data: User?) {
    constructor(error: Error) : this(error, null)
    constructor(data: User) : this(null, data)
}
