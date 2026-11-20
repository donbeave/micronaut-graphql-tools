package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import org.reactivestreams.Publisher;

@Singleton
@GraphQLRootResolver
public class BookSubscription {

    public Book published() {
        return null;
    }

}
