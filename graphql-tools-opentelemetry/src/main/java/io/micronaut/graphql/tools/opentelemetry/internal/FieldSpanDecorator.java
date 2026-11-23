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
package io.micronaut.graphql.tools.opentelemetry.internal;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer;
import io.micronaut.graphql.tools.execution.FieldDescriptor;
import io.micronaut.graphql.tools.execution.FieldExecutionKind;
import io.micronaut.graphql.tools.execution.GraphQLFieldFetcherDecorator;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decides once, at startup, which fields get a span: the fields named by {@code micronaut.graphql.tools.tracing.fields}, as
 * {@code Type.field} or {@code Type.*}. The library offers it the fields without arguments and no property read, so a
 * property read is never traced; a field of the subscription type, whose value is a stream, is not either. A selected field's
 * fetcher is wrapped in a {@link FieldSpanFetcher}; every other field gets back the fetcher it was offered, the same instance.
 * As a {@link GraphQLSchemaCustomizer} it only checks the selection against the schema, so that an entry that names nothing
 * fails the startup with a message that names the key.
 */
@Internal
@Singleton
@Requires(classes = {OpenTelemetry.class, Instrumenter.class})
@Requires(beans = OpenTelemetry.class)
@Requires(property = "micronaut.otel.enabled", notEquals = StringUtils.FALSE)
@Requires(property = GraphQLTracingConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@Requires(property = GraphQLTracingConfiguration.PREFIX + ".fields")
final class FieldSpanDecorator implements GraphQLFieldFetcherDecorator, GraphQLSchemaCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(FieldSpanDecorator.class);
    private static final String KEY = GraphQLTracingConfiguration.PREFIX + ".fields";
    private static final AttributeKey<String> FIELD_NAME = AttributeKey.stringKey("graphql.field.name");
    private static final AttributeKey<String> FIELD_PATH = AttributeKey.stringKey("graphql.field.path");

    private final OpenTelemetry openTelemetry;
    private final List<Entry> selection;

    FieldSpanDecorator(OpenTelemetry openTelemetry, GraphQLTracingConfiguration configuration) {
        this.openTelemetry = openTelemetry;
        this.selection = configuration.getFields().stream().map(String::strip).map(FieldSpanDecorator::parse).toList();
    }

    @Override
    public boolean decoratesFieldsWithoutArguments() {
        return true;
    }

    @Override
    public DataFetcher<Object> decorate(FieldDescriptor field, DataFetcher<Object> fetcher) {
        FieldCoordinates coordinates = field.coordinates();
        if (field.kind() == FieldExecutionKind.STREAM || !isSelected(coordinates)) {
            return fetcher;
        }
        String spanName = coordinates.getTypeName() + "." + coordinates.getFieldName();
        Instrumenter<DataFetchingEnvironment, Void> instrumenter = Instrumenter
                .<DataFetchingEnvironment, Void>builder(openTelemetry, RequestSpanObserver.INSTRUMENTATION_NAME, environment -> spanName)
                .addAttributesExtractor(new FieldAttributes(coordinates.getFieldName()))
                .buildInstrumenter(SpanKindExtractor.alwaysInternal());
        return new FieldSpanFetcher(fetcher, instrumenter);
    }

    @Override
    public GraphQLSchema customize(GraphQLSchema schema) {
        for (Entry entry : selection) {
            GraphQLObjectType type = schema.getObjectType(entry.type);
            if (type == null) {
                throw new ConfigurationException(KEY + " names '" + entry.text + "', and the schema has no object type '" + entry.type + "'");
            }
            if (!entry.all() && type.getFieldDefinition(entry.field) == null) {
                throw new ConfigurationException(KEY + " names '" + entry.text + "', and type '" + entry.type + "' has no field '"
                        + entry.field + "'");
            }
            if (schema.getSubscriptionType() != null && schema.getSubscriptionType().getName().equals(entry.type)) {
                LOG.warn("{} names '{}', a field of the subscription type, which is not traced: its value is a stream", KEY, entry.text);
            }
        }
        return schema;
    }

    private boolean isSelected(FieldCoordinates coordinates) {
        for (Entry entry : selection) {
            if (entry.type.equals(coordinates.getTypeName()) && (entry.all() || entry.field.equals(coordinates.getFieldName()))) {
                return true;
            }
        }
        return false;
    }

    private static Entry parse(String text) {
        int dot = text.indexOf('.');
        if (dot <= 0 || dot == text.length() - 1) {
            throw new ConfigurationException(KEY + " has '" + text + "', which is not Type.field or Type.*");
        }
        return new Entry(text, text.substring(0, dot), text.substring(dot + 1));
    }

    private record Entry(String text, String type, String field) {

        boolean all() {
            return field.equals("*");
        }
    }

    /** The name of the field is constant; its path is the one thing that differs between the calls. */
    private static final class FieldAttributes implements AttributesExtractor<DataFetchingEnvironment, Void> {

        private final String field;

        private FieldAttributes(String field) {
            this.field = field;
        }

        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext, DataFetchingEnvironment environment) {
            attributes.put(FIELD_NAME, field);
            attributes.put(FIELD_PATH, environment.getExecutionStepInfo().getPath().toString());
        }

        @Override
        public void onEnd(AttributesBuilder attributes, Context context, DataFetchingEnvironment environment, Void response,
                          Throwable error) {
        }
    }

}
