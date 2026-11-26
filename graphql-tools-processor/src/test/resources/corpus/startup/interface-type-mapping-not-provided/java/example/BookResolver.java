package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@GraphQLTypeResolver(Book.class)
public class BookResolver {

    public List<Section> sections(Book book) {
        return List.of();
    }

}
