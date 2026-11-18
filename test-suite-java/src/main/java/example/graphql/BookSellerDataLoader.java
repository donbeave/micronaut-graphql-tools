/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.graphql;

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
                                @Named(TaskExecutors.BLOCKING) ExecutorService executor) {
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
