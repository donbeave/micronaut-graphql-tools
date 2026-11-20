package example;

import jakarta.inject.Singleton;
import org.dataloader.MappedBatchLoader;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Singleton
public class BookLoader implements MappedBatchLoader<String, String> {

    @Override
    public CompletionStage<Map<String, String>> load(Set<String> keys) {
        return CompletableFuture.completedFuture(keys.stream().collect(Collectors.toMap(key -> key, key -> "book-" + key)));
    }

}
