package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@Singleton
@GraphQLTypeResolver(Slip.class)
public class SlipResolver {

    public String title(Slip slip) {
        return "resolver";
    }

}
