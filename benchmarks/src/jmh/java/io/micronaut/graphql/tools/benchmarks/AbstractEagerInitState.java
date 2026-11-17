package io.micronaut.graphql.tools.benchmarks;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.GraphQLSchemaCustomizer;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What {@code micronaut.graphql.tools.eager-init} moves: the generated schema of 1,600 fields (see
 * {@code generateLargeSchema} in build.gradle.kts), bound when the context starts ({@code true}) or when the first
 * query needs the {@code GraphQL} bean ({@code false}). A counting schema customizer, which runs once per binding,
 * lets each benchmark check that it measures the side it says.
 */
@State(Scope.Benchmark)
public abstract class AbstractEagerInitState {

    static final String QUERY = "{ root { id next { name computed(n: 1) } } }";

    @Param({"true", "false"})
    public boolean eagerInit;

    final AtomicInteger bindings = new AtomicInteger();

    private TypeDefinitionRegistry registry;

    @Setup
    public void parse() throws IOException {
        try (InputStream in = AbstractEagerInitState.class.getResourceAsStream("/large-schema.graphqls")) {
            registry = new SchemaParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
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
                        "micronaut.graphql.tools.eager-init", eagerInit))
                .singletons(registry, counting)
                .start();
    }

    /** Throws when the schema was bound (or not) at the wrong time for this parameter. */
    void checkBoundAtStart() {
        int expected = eagerInit ? 1 : 0;
        if (bindings.get() != expected) {
            throw new IllegalStateException("eager-init " + eagerInit + ": " + bindings.get()
                    + " bindings after the start, expected " + expected);
        }
    }

}
