package example.graphql.type

import example.data.User
import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType
class Profile(private val user: User) {
    val username: String get() = user.username!!
    val fullName: String get() = "${user.firstName} ${user.lastName}"
}
