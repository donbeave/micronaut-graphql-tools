package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class FirstQuery {

    public String first(FirstFilter filter) {
        return filter.getText();
    }

}
