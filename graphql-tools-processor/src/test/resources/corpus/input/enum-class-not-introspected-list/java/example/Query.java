package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import java.util.List;

@GraphQLRootResolver
public class Query {

    public int paint(List<Color> colors) {
        return colors.size();
    }

}
