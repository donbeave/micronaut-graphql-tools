package example;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class ResolverFactory {

    @Bean
    @Singleton
    public BookQuery bookQuery() {
        return new BookQuery();
    }

}
