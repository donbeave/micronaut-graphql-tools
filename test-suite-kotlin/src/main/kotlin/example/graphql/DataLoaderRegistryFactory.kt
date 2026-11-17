package example.graphql

import io.micronaut.context.annotation.Factory
import io.micronaut.runtime.http.scope.RequestScope
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.slf4j.LoggerFactory

@Factory
class DataLoaderRegistryFactory(private val bookSellerDataLoader: BookSellerDataLoader) {

    @RequestScope
    fun dataLoaderRegistry(): DataLoaderRegistry {
        val dataLoaderRegistry = DataLoaderRegistry()
        dataLoaderRegistry.register(
            BookSellerDataLoader::class.java.name,
            DataLoaderFactory.newMappedDataLoader(bookSellerDataLoader),
        )
        LOG.trace("Created new data loader registry")
        return dataLoaderRegistry
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DataLoaderRegistryFactory::class.java)
    }
}
