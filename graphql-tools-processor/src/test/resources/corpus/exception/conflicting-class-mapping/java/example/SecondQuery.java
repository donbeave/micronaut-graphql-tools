package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class SecondQuery {

    public String second(SecondFilter filter) {
        return filter.getText();
    }

}
