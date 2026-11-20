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
package io.micronaut.graphql.tools.observability.internal;

import graphql.TrivialDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.PropertyDataFetcher;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer;
import io.micronaut.graphql.tools.observability.GraphQLObservability;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides once, at startup, which fields get a span: the fields named by {@code tracing.fields}, as {@code Type.field} or
 * {@code Type.*}. A selected field's fetcher is replaced by a {@link TracedDataFetcher}; every other fetcher stays the object the
 * library bound. A property read (a trivial fetcher) is never wrapped, and neither is a field of the subscription type, whose
 * value is a stream. With nothing selected the schema is returned as it is.
 */
@Internal
@Singleton
@Requires(classes = OpenTelemetry.class)
@Requires(beans = OpenTelemetry.class)
@Requires(property = GraphQLObservability.PREFIX + ".enabled", notEquals = "false")
@Requires(property = GraphQLObservability.PREFIX + ".tracing.enabled", notEquals = "false")
@Requires(property = GraphQLObservability.PREFIX + ".tracing.fields")
final class TracingSchemaCustomizer implements GraphQLSchemaCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(TracingSchemaCustomizer.class);
    private static final String KEY = GraphQLObservability.PREFIX + ".tracing.fields";

    private final Tracer tracer;
    private final List<String> selection;

    TracingSchemaCustomizer(OpenTelemetry openTelemetry, ObservabilitySettings settings) {
        this.tracer = openTelemetry.getTracer(TracingObserver.INSTRUMENTATION_NAME);
        this.selection = settings.getTracing().getFields();
    }

    @Override
    public GraphQLSchema customize(GraphQLSchema schema) {
        if (selection.isEmpty()) {
            return schema;
        }
        GraphQLCodeRegistry registry = schema.getCodeRegistry();
        List<Wrapped> wrapped = new ArrayList<>();
        for (String entry : selection) {
            select(schema, registry, entry.strip(), wrapped);
        }
        if (wrapped.isEmpty()) {
            return schema;
        }
        GraphQLCodeRegistry replaced = registry.transform(builder -> {
            for (Wrapped each : wrapped) {
                builder.dataFetcher(each.coordinates, each.fetcher);
            }
        });
        return schema.transform(builder -> builder.codeRegistry(replaced));
    }

    private void select(GraphQLSchema schema, GraphQLCodeRegistry registry, String entry, List<Wrapped> wrapped) {
        int dot = entry.indexOf('.');
        if (dot <= 0 || dot == entry.length() - 1) {
            throw new ConfigurationException(KEY + " has '" + entry + "', which is not Type.field or Type.*");
        }
        String typeName = entry.substring(0, dot);
        String fieldName = entry.substring(dot + 1);
        GraphQLObjectType type = schema.getObjectType(typeName);
        if (type == null) {
            throw new ConfigurationException(KEY + " names '" + entry + "', and the schema has no object type '" + typeName + "'");
        }
        boolean all = fieldName.equals("*");
        if (!all && type.getFieldDefinition(fieldName) == null) {
            throw new ConfigurationException(KEY + " names '" + entry + "', and type '" + typeName + "' has no field '"
                    + fieldName + "'");
        }
        if (isSubscription(schema, type)) {
            LOG.warn("{} names '{}', a field of the subscription type, which is not traced: its value is a stream", KEY, entry);
            return;
        }
        for (GraphQLFieldDefinition definition : type.getFieldDefinitions()) {
            if (!all && !definition.getName().equals(fieldName)) {
                continue;
            }
            FieldCoordinates coordinates = FieldCoordinates.coordinates(typeName, definition.getName());
            DataFetcher<?> fetcher = registry.getDataFetcher(coordinates, definition);
            if (fetcher instanceof TrivialDataFetcher || fetcher instanceof PropertyDataFetcher || fetcher instanceof TracedDataFetcher) {
                LOG.debug("{}.{} is a property read and gets no span", typeName, definition.getName());
                continue;
            }
            wrapped.add(new Wrapped(coordinates, new TracedDataFetcher(fetcher, tracer, typeName, definition.getName())));
        }
    }

    private static boolean isSubscription(GraphQLSchema schema, GraphQLObjectType type) {
        return schema.getSubscriptionType() != null && schema.getSubscriptionType().getName().equals(type.getName());
    }

    private record Wrapped(FieldCoordinates coordinates, DataFetcher<?> fetcher) {
    }

}
