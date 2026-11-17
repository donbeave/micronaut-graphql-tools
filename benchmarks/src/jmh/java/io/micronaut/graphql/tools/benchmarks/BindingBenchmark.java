package io.micronaut.graphql.tools.benchmarks;

import graphql.GraphQL;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BindingBenchmark {

    @Param({"100", "1600", "10000"})
    public int fields;

    @Param({"false", "true"})
    public boolean record;

    private TypeDefinitionRegistry registry;

    @Setup
    public void parse() {
        registry = LargeSchema.registry(fields);
    }

    @Benchmark
    public Object bindLargeSchema() {
        try (ApplicationContext context = start()) {
            Object schema = context.getBean(GraphQL.class).getGraphQLSchema();
            // The checks run inside the invocation, after the binding. A context started in @Setup would make the
            // measured start the second start in the JVM, with the classes and the static state of the first.
            LargeSchema.checkBound(context);
            if (LargeSchema.manifestAvailable(context) != record) {
                throw new IllegalStateException("The manifest is " + (record ? "not " : "") + "recorded, but record is "
                        + record);
            }
            return schema;
        }
    }

    private ApplicationContext start() {
        return ApplicationContext.builder()
                .properties(Map.of(
                        "graphql.graphql-ws.enabled", false,
                        "graphql.graphiql.enabled", false,
                        "benchmarks.large", true,
                        LargeSchema.FIELDS, fields,
                        "micronaut.graphql.tools.manifest.record", record))
                .singletons(registry)
                .start();
    }
}
