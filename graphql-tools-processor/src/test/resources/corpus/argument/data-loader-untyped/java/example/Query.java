package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import org.dataloader.DataLoader;

@GraphQLRootResolver
public class Query {

    @SuppressWarnings("rawtypes")
    public String book(DataLoader loader, String id) {
        return id;
    }

}
