package example;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import org.dataloader.DataLoaderRegistry;

@Factory
public class RegistryFactory {

    @Bean
    @Singleton
    DataLoaderRegistry registry() {
        return new DataLoaderRegistry();
    }

}
