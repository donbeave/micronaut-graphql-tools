package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import java.util.List;

@Singleton
@GraphQLRootResolver
public class Query {

    public int paint(List<Color> colors) {
        return colors.size();
    }

}
