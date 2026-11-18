package io.micronaut.graphql.tools.benchmarks;

import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.GraphQLSchemaCustomizer;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public abstract class AbstractEagerInitState {

    static final String QUERY = "{ root { id next { name computed(n: 1) } } }";

    static final int FIELDS = 1600;

    @Param({"true", "false"})
    public boolean eagerInit;

    final AtomicInteger bindings = new AtomicInteger();

    private TypeDefinitionRegistry registry;

    @Setup
    public void parse() {
        registry = LargeSchema.registry(FIELDS);
    }

    ApplicationContext start() {
        bindings.set(0);
        GraphQLSchemaCustomizer counting = schema -> {
            bindings.incrementAndGet();
            return schema;
        };
        return ApplicationContext.builder()
                .properties(Map.of(
                        "graphql.graphql-ws.enabled", false,
                        "graphql.graphiql.enabled", false,
                        "benchmarks.large", true,
                        LargeSchema.FIELDS, FIELDS,
                        "micronaut.graphql.tools.eager-init", eagerInit))
                .singletons(registry, counting)
                .start();
    }

    void checkBoundAtStart() {
        int expected = eagerInit ? 1 : 0;
        if (bindings.get() != expected) {
            throw new IllegalStateException("eager-init " + eagerInit + ": " + bindings.get()
                    + " bindings after the start, expected " + expected);
        }
    }

}
