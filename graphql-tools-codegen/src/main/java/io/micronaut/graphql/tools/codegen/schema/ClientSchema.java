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
package io.micronaut.graphql.tools.codegen.schema;

import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.schema.idl.errors.SchemaProblem;
import io.micronaut.graphql.tools.codegen.Diagnostic;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Builds the schema that the operations of client mode are validated against, the same way the compile-time checks build it: an
 * unexecutable schema of the merged definitions. graphql-java builds a schema recursively through the type references, so a long
 * chain of types overflows the stack of a default thread; the build runs on a thread with a large stack, and a stack that is still
 * too small is a diagnostic, not a crash.
 */
final class ClientSchema {

    // memory is committed as the build uses it
    private static final long STACK_BYTES = 512L * 1024 * 1024;
    private static final String THREAD = "graphql-tools-codegen-schema";

    private ClientSchema() {
    }

    static Built build(MergedSchema merged) {
        AtomicReference<GraphQLSchema> schema = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable task = () -> {
            try {
                schema.set(read(merged));
            } catch (Throwable e) {
                failure.set(e);
            }
        };
        Thread worker = new Thread(null, task, THREAD, STACK_BYTES);
        worker.setDaemon(true);
        try {
            worker.start();
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Built(null, invalid("The build of the schema was interrupted."));
        } catch (OutOfMemoryError e) {
            // the system gave no thread with that stack: build here, where an overflow is a diagnostic too
            task.run();
        }
        Throwable thrown = failure.get();
        if (thrown == null) {
            return new Built(schema.get(), null);
        }
        if (thrown instanceof StackOverflowError) {
            return new Built(null, invalid("The schema has a chain of type references that is too long to build (the build ran out of"
                    + " stack). Shorten the chain of type references."));
        }
        if (thrown instanceof SchemaProblem problem) {
            return new Built(null, invalid(problem.getErrors().stream().map(GraphQLError::getMessage).collect(Collectors.joining("; "))));
        }
        if (thrown instanceof RuntimeException exception) {
            return new Built(null, invalid("The schema cannot be built: " + exception));
        }
        if (thrown instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(thrown);
    }

    private static GraphQLSchema read(MergedSchema merged) {
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        merged.definitions.forEach(definition -> registry.add(definition).ifPresent(error -> {
            throw new SchemaProblem(List.of(error));
        }));
        return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
    }

    private static Diagnostic invalid(String reason) {
        return new Diagnostic(Diagnostic.Severity.ERROR, Diagnostic.CLIENT_SCHEMA_INVALID,
                "The schema cannot be used to check the operation documents: " + reason, null, null);
    }

    /**
     * What a build produced.
     *
     * @param schema     the schema, {@code null} when it cannot be built
     * @param diagnostic why it cannot, {@code null} when it was built
     */
    record Built(GraphQLSchema schema, Diagnostic diagnostic) {
    }
}
