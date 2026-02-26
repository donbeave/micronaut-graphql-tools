package example.graphql;

import io.micronaut.context.annotation.Factory;
import io.micronaut.runtime.http.scope.RequestScope;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Factory
public class DataLoaderRegistryFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DataLoaderRegistryFactory.class);

    private final BookSellerDataLoader bookSellerDataLoader;

    public DataLoaderRegistryFactory(BookSellerDataLoader bookSellerDataLoader) {
        this.bookSellerDataLoader = bookSellerDataLoader;
    }

    @RequestScope
    public DataLoaderRegistry dataLoaderRegistry() {
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register(BookSellerDataLoader.class.getName(), DataLoader.newMappedDataLoader(bookSellerDataLoader));
        LOG.trace("Created new data loader registry");
        return dataLoaderRegistry;
    }

}
