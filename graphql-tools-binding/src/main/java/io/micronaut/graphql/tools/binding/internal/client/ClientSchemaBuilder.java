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
package io.micronaut.graphql.tools.binding.internal.client;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.schema.idl.errors.SchemaProblem;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSourceException;
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the schema that a client document is validated against. graphql-java builds a schema recursively through the type
 * references, so a long chain of types (about 1,300 on a default compiler thread) overflows the stack; the build therefore
 * runs on a thread of its own with a large stack, and a stack that is still too small is a finding, not a crash of the
 * compiler.
 */
@Internal
public final class ClientSchemaBuilder {

    /** The stack of the thread that builds a schema: memory is committed as the build uses it. */
    public static final long STACK_BYTES = 512L * 1024 * 1024;

    private static final String THREAD = "graphql-tools-client-schema";

    private ClientSchemaBuilder() {
    }

    /**
     * @param name       the schema name
     * @param sources    the {@code *.graphqls} sources of the schema
     * @param stackBytes the stack of the thread that builds it
     * @return the schema, or a {@code client-schema-invalid} finding
     */
    public static Built build(String name, List<SchemaSources.Source> sources, long stackBytes) {
        AtomicReference<GraphQLSchema> schema = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(null, () -> {
            try {
                schema.set(read(sources));
            } catch (Throwable e) {
                failure.set(e);
            }
        }, THREAD, stackBytes);
        worker.setDaemon(true);
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Built(null, invalid(name, "the build of the schema was interrupted", null));
        }
        Throwable thrown = failure.get();
        if (thrown == null) {
            return new Built(schema.get(), null);
        }
        if (thrown instanceof StackOverflowError) {
            return new Built(null, invalid(name, "the schema has a chain of type references that is too long to build (the build "
                    + "ran out of stack)", "shorten the chain of type references, or compile with "
                    + "micronaut.graphql.tools.processor.client.checks=false"));
        }
        if (thrown instanceof SchemaSourceException || thrown instanceof SchemaProblem) {
            return new Built(null, invalid(name, thrown.getMessage(), null));
        }
        if (thrown instanceof RuntimeException exception) {
            return new Built(null, invalid(name, "the schema cannot be built: " + exception, null));
        }
        if (thrown instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(thrown);
    }

    /**
     * @param directories the directories of the {@code *.graphqls} files
     * @return the sources, named by absolute path
     */
    public static List<SchemaSources.Source> sources(List<Path> directories) {
        return SchemaSources.readDirectories(directories).stream()
                .map(it -> new SchemaSources.Source(directories.size() > 1 ? Path.of(it.name()).toAbsolutePath().toString()
                        : directories.get(0).resolve(it.name()).toAbsolutePath().toString(), it.text()))
                .toList();
    }

    private static GraphQLSchema read(List<SchemaSources.Source> sources) {
        SchemaSources.Loaded loaded = SchemaSources.load(sources);
        if (loaded.read().isEmpty()) {
            throw new SchemaSourceException("no schema definition in " + sources.stream().map(SchemaSources.Source::name).toList()
                    + ": every file is empty or holds only operations");
        }
        TypeDefinitionRegistry registry = loaded.registry();
        return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
    }

    private static Finding invalid(String name, String sentence, @Nullable String fix) {
        return new Finding(BindingCode.CLIENT_SCHEMA_INVALID.getCode(), name, "the client schema " + name + " is not valid: "
                + sentence, null, null, null, fix == null ? "correct the schema file at the position that the message gives, or "
                + "compile with micronaut.graphql.tools.processor.client.checks=false" : fix);
    }

    /**
     * What a build produced.
     *
     * @param schema  the schema, null when it cannot be built
     * @param problem why it cannot, null when it was built
     */
    public record Built(@Nullable GraphQLSchema schema, @Nullable Finding problem) {
    }
}
