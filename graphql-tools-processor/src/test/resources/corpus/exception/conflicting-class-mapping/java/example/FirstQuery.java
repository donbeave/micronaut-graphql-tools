package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class FirstQuery {

    public String first(FirstFilter filter) {
        return filter.getText();
    }

}
