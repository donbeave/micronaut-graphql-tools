package example.graphql.type.resolver;

import example.ElapsedTime;
import example.data.Book;
import example.data.User;
import example.data.UserBookRepository;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

import java.util.List;

@GraphQLTypeResolver(User.class)
public class UserResolver {

    private final UserBookRepository userBookRepository;

    public UserResolver(UserBookRepository userBookRepository) {
        this.userBookRepository = userBookRepository;
    }

    @ElapsedTime
    public List<Book> books(User user) {
        return userBookRepository.findAllByUserId(user);
    }

}
