package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.annotation.PreDestroy

@Singleton
@GraphQLRootResolver
class Query {

    String hello() {
        return "hi"
    }

    @EventListener
    void onStartup(ServerStartupEvent event) {
    }

    @Scheduled(fixedDelay = "1h")
    void refresh() {
    }

    @PreDestroy
    void close() {
    }

}
