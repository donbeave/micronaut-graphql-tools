package io.micronaut.graphql.tools.ignore;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;

/**
 * A root resolver with a method of each kind that binds no field: an event listener, a scheduled method, a pre-destroy
 * callback and a helper marked {@code @GraphQLIgnore}.
 */
@Requires(property = "spec.name", value = "IgnoreSpec")
@GraphQLRootResolver
public class JavaIgnoreQuery {

    public JavaIgnoreItem item() {
        return new JavaIgnoreItem();
    }

    public JavaIgnoreLevel level() {
        return JavaIgnoreLevel.HIGH;
    }

    public String describe(JavaIgnoreFilter filter) {
        return filter.getText();
    }

    @EventListener
    public void onStartup(StartupEvent event) {
    }

    @Scheduled(initialDelay = "1h", fixedDelay = "1h")
    public void tick() {
    }

    @PreDestroy
    public void close() {
    }

    @GraphQLIgnore
    public String helper(String value) {
        return value;
    }

}
