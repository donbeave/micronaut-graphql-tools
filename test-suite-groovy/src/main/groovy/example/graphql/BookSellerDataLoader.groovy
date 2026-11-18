package example.graphql

import example.data.BookSeller
import example.data.BookSellerRepository
import groovy.transform.CompileStatic
import io.micronaut.scheduling.TaskExecutors
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.dataloader.MappedBatchLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService

@CompileStatic
@Singleton
class BookSellerDataLoader implements MappedBatchLoader<String, List<BookSeller>> {

    private static final Logger LOG = LoggerFactory.getLogger(BookSellerDataLoader)

    private final BookSellerRepository bookSellerRepository
    private final ExecutorService executor

    BookSellerDataLoader(BookSellerRepository bookSellerRepository, @Named(TaskExecutors.BLOCKING) ExecutorService executor) {
        this.bookSellerRepository = bookSellerRepository
        this.executor = executor
    }

    @Override
    CompletionStage<Map<String, List<BookSeller>>> load(Set<String> keys) {
        return CompletableFuture.<Map<String, List<BookSeller>>> supplyAsync(() -> {
            Map<String, List<BookSeller>> grouped = bookSellerRepository.findAllByBookIds(keys).groupBy { it.bookId }
            LOG.info('Loaded book sellers')
            Map<String, List<BookSeller>> result = [:]
            keys.each { String id -> result.put(id, grouped.getOrDefault(id, Collections.<BookSeller> emptyList())) }
            result
        }, executor)
    }
}
