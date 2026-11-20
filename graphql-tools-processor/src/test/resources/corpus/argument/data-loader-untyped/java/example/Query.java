package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import org.dataloader.DataLoader;
import jakarta.inject.Singleton;

@Singleton
@GraphQLRootResolver
public class Query {

    @SuppressWarnings("rawtypes")
    public String book(DataLoader loader, String id) {
        return id;
    }

}
