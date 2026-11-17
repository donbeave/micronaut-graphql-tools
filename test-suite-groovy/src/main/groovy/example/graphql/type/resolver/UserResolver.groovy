package example.graphql.type.resolver

import example.ElapsedTime
import example.data.Book
import example.data.User
import example.data.UserBookRepository
import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@CompileStatic
@GraphQLTypeResolver(User)
class UserResolver {

    private final UserBookRepository userBookRepository

    UserResolver(UserBookRepository userBookRepository) {
        this.userBookRepository = userBookRepository
    }

    @ElapsedTime
    List<Book> books(User user) {
        userBookRepository.findAllByUserId(user)
    }
}
