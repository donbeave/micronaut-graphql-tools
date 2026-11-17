package example.data

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected

@CompileStatic
@Introspected
class User {
    Long id
    String username
    String firstName
    String lastName
    String password
}
