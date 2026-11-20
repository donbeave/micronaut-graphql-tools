package example

import jakarta.inject.Singleton
import org.dataloader.MappedBatchLoader

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@Singleton
class BookLoader implements MappedBatchLoader<String, String> {

    @Override
    CompletionStage<Map<String, String>> load(Set<String> keys) {
        Map<String, String> books = [:]
        keys.each { String key -> books.put(key, 'book-' + key) }
        return CompletableFuture.completedFuture(books)
    }

}
