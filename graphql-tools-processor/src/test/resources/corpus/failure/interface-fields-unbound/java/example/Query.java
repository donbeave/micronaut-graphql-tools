package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import java.util.List;

@GraphQLRootResolver
public class Query {

    public List<Section> sections() {
        return List.of();
    }

}
