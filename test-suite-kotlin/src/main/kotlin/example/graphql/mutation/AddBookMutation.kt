package example.graphql.mutation

import example.ElapsedTime
import example.data.AuthenticationService
import example.data.BookRepository
import example.data.UserBookRepository
import example.graphql.type.AddBookPayload
import example.graphql.type.SecurityError
import example.graphql.type.SecurityErrorCode
import example.graphql.type.ValidationError
import example.graphql.type.ValidationErrorCode
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
open class AddBookMutation(
    private val authenticationService: AuthenticationService,
    private val bookRepository: BookRepository,
    private val userBookRepository: UserBookRepository,
) {

    @ElapsedTime
    open fun addBook(id: String): AddBookPayload {
        val currentUser = authenticationService.currentUser
            ?: return AddBookPayload(SecurityError("Not logged in.", SecurityErrorCode.NOT_AUTHENTICATED))
        val book = bookRepository.findById(id)
            ?: return AddBookPayload(ValidationError("The book with id $id not found.", ValidationErrorCode.NOT_FOUND))
        userBookRepository.addBook(currentUser, book)
        return AddBookPayload()
    }
}
