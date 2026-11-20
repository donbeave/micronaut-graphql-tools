package example;

import io.micronaut.graphql.tools.resolver.MutationResolver;
import jakarta.inject.Singleton;

@Singleton
public class BookMutation implements MutationResolver {

    public String ping() {
        return "pong";
    }

    public String book(String id) {
        return id;
    }

}
