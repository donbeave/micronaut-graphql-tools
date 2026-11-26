package example;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.dataloader.MappedBatchLoader;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@Named("names")
public class NameLoader implements MappedBatchLoader<String, String> {

    @Override
    public CompletionStage<Map<String, String>> load(Set<String> keys) {
        return CompletableFuture.completedFuture(Map.of());
    }

}
