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
import io.micronaut.graphql.tools.manifest.BindingManifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The generated large schemas of the start-up benchmarks (see {@code generateLargeSchema} in build.gradle.kts): one per
 * field count and shape, 10 fields per object type. The {@link #RINGS} shape is object types linked in rings; the
 * {@link #INTERFACES} shape is many interfaces, the types that implement them, shared fields and a type-mapping
 * dictionary with entries mapped to interfaces. {@link #FIELDS} (rings) or {@link #INTERFACE_FIELDS} selects the one a
 * context binds.
 */
final class LargeSchema {

    /** The property that selects the generated schema of a context. */
    static final String FIELDS = "benchmarks.large.fields";

    /** The property that selects the generated interface-heavy schema of a context. */
    static final String INTERFACE_FIELDS = "benchmarks.interface.fields";

    /** The shape of object types linked in rings. */
    static final String RINGS = "rings";

    /** The interface-heavy shape. */
    static final String INTERFACES = "interfaces";

    private LargeSchema() {
    }

    /** The context properties that select the schema of {@code shape} with {@code fields} fields. */
    static Map<String, Object> properties(String shape, int fields) {
        return Map.of(
                "graphql.graphql-ws.enabled", false,
                "graphql.graphiql.enabled", false,
                "benchmarks.large", true,
                INTERFACES.equals(shape) ? INTERFACE_FIELDS : FIELDS, fields);
    }

    /** The schema of {@code shape} with {@code fields} fields, parsed. */
    static TypeDefinitionRegistry registry(String shape, int fields) {
        return parse(INTERFACES.equals(shape) ? "/interface-schema-" + fields + ".graphqls"
                : "/large-schema-" + fields + ".graphqls");
    }

    /** The ring schema with {@code fields} fields, parsed. */
    static TypeDefinitionRegistry registry(int fields) {
        return registry(RINGS, fields);
    }

    private static TypeDefinitionRegistry parse(String resource) {
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
    static void checkBound(ApplicationContext context, String shape) {
        String type = INTERFACES.equals(shape) ? "Obj0" : "Node0";
        if (context.getBean(GraphQL.class).getGraphQLSchema().getObjectType(type) == null) {
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
