package example.data

import io.micronaut.core.annotation.Introspected

@Introspected
class User(
    var id: Long? = null,
    var username: String? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    var password: String? = null,
)
