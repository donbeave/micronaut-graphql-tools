package example

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class ResolverFactory {

    @Bean
    @Singleton
    BookQuery bookQuery() {
        return new BookQuery()
    }

}
