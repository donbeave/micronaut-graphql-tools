package example;

import io.micronaut.graphql.tools.resolver.MutationResolver;
import jakarta.inject.Singleton;

@Singleton
public class StockMutation implements MutationResolver {

    public String stock() {
        return "mutated";
    }

}
