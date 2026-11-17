package example.graphql.type.resolver

import example.ElapsedTime
import example.data.Book
import example.data.User
import example.data.UserBookRepository
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(User::class)
open class UserResolver(private val userBookRepository: UserBookRepository) {

    @ElapsedTime
    open fun books(user: User): List<Book> = userBookRepository.findAllByUserId(user)
}
