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

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Factory
import io.micronaut.runtime.http.scope.RequestScope
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
@Factory
class DataLoaderRegistryFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DataLoaderRegistryFactory)

    private final BookSellerDataLoader bookSellerDataLoader

    DataLoaderRegistryFactory(BookSellerDataLoader bookSellerDataLoader) {
        this.bookSellerDataLoader = bookSellerDataLoader
    }

    @RequestScope
    DataLoaderRegistry dataLoaderRegistry() {
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register(BookSellerDataLoader.name, DataLoaderFactory.newMappedDataLoader(bookSellerDataLoader))
        LOG.trace('Created new data loader registry')
        return dataLoaderRegistry
    }
}
