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

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.micronaut.graphql.tools.internal.protection.DocumentCache;
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

/**
 * The repeat-request path with and without the library's parsed-document cache ({@code protection.document-cache-size}),
 * on one {@code GraphQL}: {@code off} parses and validates the query text on every execution, {@code on} does it once. The
 * operation is the same query text each time, over a root object, so the resolvers cost little and parse plus validation
 * is what the cache removes. {@code small} is one selection of eleven fields; {@code wide} repeats it under 200 aliases
 * (about 2,200 fields), the size of the larger operations that real clients send.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class DocumentCacheBenchmark {

    @Param({"off", "on"})
    public String cache;

    @Param({"small", "wide"})
    public String query;

    private GraphQL graphQL;
    private String text;

    @Setup
    public void prepare() {
        GraphQL plain = HopSchema.build(Map.of("root", env -> HopSchema.NODE, "items", env -> HopSchema.NODES),
                method -> method::apply, null);
        graphQL = cache.equals("on") ? plain.transform(builder -> builder.preparsedDocumentProvider(new DocumentCache(1000))) : plain;
        text = query.equals("small") ? HopSchema.ROOT_QUERY : wide();
        ExecutionResult result = graphQL.execute(text);
        if (!result.getErrors().isEmpty()) {
            throw new IllegalStateException(result.getErrors().toString());
        }
    }

    private static String wide() {
        StringBuilder builder = new StringBuilder("{");
        String selection = HopSchema.ROOT_QUERY.substring(HopSchema.ROOT_QUERY.indexOf("root"), HopSchema.ROOT_QUERY.length() - 2);
        for (int i = 0; i < 200; i++) {
            builder.append(" a").append(i).append(": ").append(selection);
        }
        return builder.append(" }").toString();
    }

    @Benchmark
    public ExecutionResult execute() {
        return graphQL.execute(text);
    }
}
