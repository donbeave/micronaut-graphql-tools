package example;

import io.micronaut.graphql.tools.resolver.QueryResolver;
import jakarta.inject.Singleton;

@Singleton
public class StockQuery implements QueryResolver {

    public String stock() {
        return "queried";
    }

}
