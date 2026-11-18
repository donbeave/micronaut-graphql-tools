package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class SecondQuery {

    public String second(SecondFilter filter) {
        return filter.getText();
    }

}
