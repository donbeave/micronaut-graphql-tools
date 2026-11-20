package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query implements Contract {

    @Override
    public String size(Integer width, Integer length) {
        return "long=" + length + " short=" + width;
    }

}
