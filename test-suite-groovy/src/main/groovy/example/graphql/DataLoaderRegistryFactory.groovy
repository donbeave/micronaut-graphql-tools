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
