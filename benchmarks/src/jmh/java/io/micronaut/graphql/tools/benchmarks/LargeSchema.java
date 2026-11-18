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
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.BindingManifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The generated large schemas of the start-up benchmarks (see {@code generateLargeSchema} in build.gradle.kts): one per
 * field count, 10 fields per object type. {@link #FIELDS} selects the one a context binds.
 */
final class LargeSchema {

    /** The property that selects the generated schema of a context. */
    static final String FIELDS = "benchmarks.large.fields";

    private LargeSchema() {
    }

    /** The schema with {@code fields} fields, parsed. */
    static TypeDefinitionRegistry registry(int fields) {
        String resource = "/large-schema-" + fields + ".graphqls";
        try (InputStream in = LargeSchema.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No generated schema " + resource);
            }
            return new SchemaParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }

    /** Throws when the large schema of the context is not bound. */
    static void checkBound(ApplicationContext context) {
        if (context.getBean(GraphQL.class).getGraphQLSchema().getObjectType("Node0") == null) {
            throw new IllegalStateException("The large schema was not bound");
        }
    }

    /** Whether the context holds the binding manifest. */
    static boolean manifestAvailable(ApplicationContext context) {
        try {
            context.getBean(BindingManifest.class);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
