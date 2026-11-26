package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;
import org.dataloader.DataLoader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@GraphQLRootResolver
public class Query {

    public CompletionStage<String> hello(String prefix, DataLoader<Integer, Integer> loader) {
        return CompletableFuture.completedFuture(prefix);
    }

}
