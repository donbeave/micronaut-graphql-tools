package example

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
open class ResolverFactory {

    @Bean
    @Singleton
    open fun bookQuery(): BookQuery = BookQuery()

}
