package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.annotation.PreDestroy

@Singleton
@GraphQLRootResolver
open class Query {

    open fun hello(): String = "hi"

    @EventListener
    open fun onStartup(event: ServerStartupEvent) {
    }

    @Scheduled(fixedDelay = "1h")
    open fun refresh() {
    }

    @PreDestroy
    open fun close() {
    }

}
