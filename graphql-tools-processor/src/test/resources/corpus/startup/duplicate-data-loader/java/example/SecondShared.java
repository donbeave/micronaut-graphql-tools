package example;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.dataloader.BatchLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@Named("shared")
public class SecondShared implements BatchLoader<Long, String> {

    @Override
    public CompletionStage<List<String>> load(List<Long> keys) {
        return CompletableFuture.completedFuture(List.of());
    }

}
