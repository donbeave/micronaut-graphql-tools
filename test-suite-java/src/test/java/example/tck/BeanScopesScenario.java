package example.tck;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.runtime.http.scope.RequestScope;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The fixtures of the TCK scenario {@code bean-scopes}: a resolver is a singleton, and the beans it uses keep their own scope.
 */
public final class BeanScopesScenario {

    private BeanScopesScenario() {
    }

    @Singleton
    @Requires(property = "tck.scenario", value = "bean-scopes")
    public static class IdSequence {
        private final AtomicInteger next = new AtomicInteger();

        int next() {
            return next.incrementAndGet();
        }
    }

    @RequestScope
    @Requires(property = "tck.scenario", value = "bean-scopes")
    public static class RequestCounter {
        private final AtomicInteger count = new AtomicInteger();

        int next() {
            return count.incrementAndGet();
        }
    }

    @Prototype
    @Requires(property = "tck.scenario", value = "bean-scopes")
    public static class Ticket {
        private final int id;

        public Ticket(IdSequence sequence) {
            this.id = sequence.next();
        }

        int id() {
            return id;
        }
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "bean-scopes")
    public static class Queries {

        private final AtomicInteger singletonCount = new AtomicInteger();
        private final RequestCounter requestCounter;
        private final Ticket injected;
        private final BeanProvider<Ticket> tickets;

        public Queries(RequestCounter requestCounter, Ticket injected, BeanProvider<Ticket> tickets) {
            this.requestCounter = requestCounter;
            this.injected = injected;
            this.tickets = tickets;
        }

        public int singletonCount() {
            return singletonCount.incrementAndGet();
        }

        public int requestScopedCount() {
            return requestCounter.next();
        }

        public int injectedPrototypeId() {
            return injected.id();
        }

        public int freshPrototypeId() {
            return tickets.get().id();
        }
    }

}
