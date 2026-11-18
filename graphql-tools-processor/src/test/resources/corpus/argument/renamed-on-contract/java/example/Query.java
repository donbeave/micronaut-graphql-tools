package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query implements Contract {

    @Override
    public String size(Integer width, Integer length) {
        return "long=" + length + " short=" + width;
    }

}
