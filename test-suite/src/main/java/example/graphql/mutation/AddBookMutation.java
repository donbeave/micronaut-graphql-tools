package example.graphql.mutation;

import example.ElapsedTime;
import example.data.AuthenticationService;
import example.data.Book;
import example.data.BookRepository;
import example.data.User;
import example.data.UserBookRepository;
import example.graphql.type.AddBookPayload;
import example.graphql.type.SecurityError;
import example.graphql.type.SecurityErrorCode;
import example.graphql.type.ValidationError;
import example.graphql.type.ValidationErrorCode;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.Optional;

@GraphQLRootResolver
public class AddBookMutation {

    private final AuthenticationService authenticationService;
    private final BookRepository bookRepository;
    private final UserBookRepository userBookRepository;

    public AddBookMutation(AuthenticationService authenticationService, BookRepository bookRepository,
                           UserBookRepository userBookRepository) {
        this.authenticationService = authenticationService;
        this.bookRepository = bookRepository;
        this.userBookRepository = userBookRepository;
    }

    @ElapsedTime
    public AddBookPayload addBook(String id) {
        Optional<User> currentUser = authenticationService.getCurrentUser();

        if (currentUser.isEmpty()) {
            return new AddBookPayload(new SecurityError("Not logged in.", SecurityErrorCode.NOT_AUTHENTICATED));
        }

        Optional<Book> book = bookRepository.findById(id);

        if (book.isEmpty()) {
            String errorMessage = "The book with id " + id + " not found.";
            return new AddBookPayload(new ValidationError(errorMessage, ValidationErrorCode.NOT_FOUND));
        }

        userBookRepository.addBook(currentUser.get(), book.get());

        return new AddBookPayload();
    }

}
