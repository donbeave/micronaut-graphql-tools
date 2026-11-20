package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import org.dataloader.DataLoader;

import java.util.concurrent.CompletionStage;
import jakarta.inject.Singleton;

@Singleton
@GraphQLRootResolver
public class Query {

    public CompletionStage<String> book(DataLoader<String, String> loader, String id) {
        return loader.load(id);
    }

}
