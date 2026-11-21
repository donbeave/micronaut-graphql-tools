package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class BookQuery {

    public io.reactivex.rxjava3.core.Completable done() {
        return null;
    }

    public io.reactivex.rxjava3.core.Completable mustReturn() {
        return null;
    }

}
