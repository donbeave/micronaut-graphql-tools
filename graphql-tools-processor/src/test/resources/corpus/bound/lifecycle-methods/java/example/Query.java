package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;

@GraphQLRootResolver
public class Query {

    public String hello() {
        return "hi";
    }

    @EventListener
    public void onStartup(ServerStartupEvent event) {
    }

    @Scheduled(fixedDelay = "1h")
    public void refresh() {
    }

    @PreDestroy
    public void close() {
    }

}
