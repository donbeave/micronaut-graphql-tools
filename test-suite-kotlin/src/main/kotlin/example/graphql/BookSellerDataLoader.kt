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
