package example.graphql;

import example.ElapsedTimeInterceptor;
import example.data.BookSeller;
import example.data.BookSellerRepository;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.dataloader.MappedBatchLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Singleton
public class BookSellerDataLoader implements MappedBatchLoader<String, List<BookSeller>> {

    private static final Logger LOG = LoggerFactory.getLogger(BookSellerDataLoader.class);

    private final BookSellerRepository bookSellerRepository;
    private final ExecutorService executor;

    public BookSellerDataLoader(BookSellerRepository bookSellerRepository,
                                @Named(TaskExecutors.IO) ExecutorService executor) {
        this.bookSellerRepository = bookSellerRepository;
        this.executor = executor;
    }

    @Override
    public CompletionStage<Map<String, List<BookSeller>>> load(Set<String> keys) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, List<BookSeller>> grouped = bookSellerRepository.findAllByBookIds(keys).stream()
                    .collect(Collectors.groupingBy(BookSeller::getBookId));

            LOG.info("Loaded book sellers");

            return keys
                    .stream()
                    .collect(Collectors.toMap(id -> id, e -> grouped.getOrDefault(e, Collections.emptyList())));
        }, executor);
    }

}
