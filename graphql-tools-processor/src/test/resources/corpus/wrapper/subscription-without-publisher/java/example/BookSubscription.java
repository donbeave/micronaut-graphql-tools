package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import org.reactivestreams.Publisher;

@GraphQLRootResolver
public class BookSubscription {

    public Book published() {
        return null;
    }

}
