package example.graphql

import example.data.BookSeller
import example.data.BookSellerRepository
import io.micronaut.scheduling.TaskExecutors
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.dataloader.MappedBatchLoader
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService

@Singleton
class BookSellerDataLoader(
    private val bookSellerRepository: BookSellerRepository,
    @Named(TaskExecutors.BLOCKING) private val executor: ExecutorService,
) : MappedBatchLoader<String, List<BookSeller>> {

    override fun load(keys: Set<String>): CompletionStage<Map<String, List<BookSeller>>> =
        CompletableFuture.supplyAsync({
            val grouped = bookSellerRepository.findAllByBookIds(keys).groupBy { it.bookId }
            LOG.info("Loaded book sellers")
            keys.associateWith { grouped[it] ?: emptyList() }
        }, executor)

    companion object {
        private val LOG = LoggerFactory.getLogger(BookSellerDataLoader::class.java)
    }
}
