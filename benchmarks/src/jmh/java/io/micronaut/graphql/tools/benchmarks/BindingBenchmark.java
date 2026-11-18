/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.benchmarks;

import graphql.GraphQL;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.binding.BindingCode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BindingBenchmark {

    @Param({"100", "1600", "3000", "10000", "20000"})
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

    // -Dbenchmarks.checks=ignore (a JMH -jvmArgs value) starts every context with all ten checks at ignore, to measure
    // what the checks cost: the default is every check at fail
    private ApplicationContext start() {
        Map<String, Object> properties = new HashMap<>(Map.of(
                "graphql.graphql-ws.enabled", false,
                "graphql.graphiql.enabled", false,
                "benchmarks.large", true,
                LargeSchema.FIELDS, fields,
                "micronaut.graphql.tools.manifest.record", record));
        if ("ignore".equals(System.getProperty("benchmarks.checks"))) {
            for (BindingCode code : BindingCode.values()) {
                if (code.check()) {
                    properties.put("micronaut.graphql.tools.checks." + code.code(), "ignore");
                }
            }
        }
        return ApplicationContext.builder()
                .properties(properties)
                .singletons(registry)
                .start();
    }
}
