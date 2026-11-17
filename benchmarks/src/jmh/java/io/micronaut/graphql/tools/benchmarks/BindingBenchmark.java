package io.micronaut.graphql.tools.benchmarks;

import graphql.GraphQL;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.BindingManifest;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BindingBenchmark {

    @Param({"false", "true"})
    public boolean record;

    private TypeDefinitionRegistry registry;

    @Setup
    public void parse() throws IOException {
        try (InputStream in = BindingBenchmark.class.getResourceAsStream("/large-schema.graphqls")) {
            registry = new SchemaParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        check();
    }

    private void check() {
        try (ApplicationContext context = start()) {
            if (context.getBean(GraphQL.class).getGraphQLSchema().getObjectType("Node159") == null) {
                throw new IllegalStateException("The large schema was not bound");
            }
            boolean manifestAvailable;
            try {
                context.getBean(BindingManifest.class);
                manifestAvailable = true;
            } catch (RuntimeException e) {
                manifestAvailable = false;
            }
            if (manifestAvailable != record) {
                throw new IllegalStateException("The manifest is " + (manifestAvailable ? "" : "not ")
                        + "recorded, but record is " + record);
            }
        }
    }

    @Benchmark
    public Object bindLargeSchema() {
        try (ApplicationContext context = start()) {
            return context.getBean(GraphQL.class).getGraphQLSchema();
        }
    }

    private ApplicationContext start() {
        return ApplicationContext.builder()
                .properties(Map.of(
                        "graphql.graphql-ws.enabled", false,
                        "graphql.graphiql.enabled", false,
                        "benchmarks.large", true,
                        "micronaut.graphql.tools.manifest.record", record))
                .singletons(registry)
                .start();
    }
}
