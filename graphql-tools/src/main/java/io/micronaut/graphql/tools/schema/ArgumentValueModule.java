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
package io.micronaut.graphql.tools.schema;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.ArgumentValue;

import java.io.IOException;

/**
 * Reads input class properties declared as {@link ArgumentValue}: a present value becomes
 * {@link ArgumentValue#ofNullable(Object)} of the converted value, an explicit {@code null} becomes
 * {@code ArgumentValue.ofNullable(null)}. Jackson does not call a setter for an absent field;
 * {@link InputValueConverter} sets those properties to {@link ArgumentValue#omitted()} after the conversion.
 *
 * @since 1.0.0
 */
@Internal
public final class ArgumentValueModule extends SimpleModule {

    /**
     * Creates the module.
     */
    public ArgumentValueModule() {
        super("micronaut-graphql-tools-argument-value");
        addDeserializer(ArgumentValue.class, new ArgumentValueDeserializer(null));
    }

    @SuppressWarnings("rawtypes")
    private static final class ArgumentValueDeserializer extends StdDeserializer<ArgumentValue>
            implements ContextualDeserializer {

        private final JsonDeserializer<Object> valueDeserializer;

        ArgumentValueDeserializer(JsonDeserializer<Object> valueDeserializer) {
            super(ArgumentValue.class);
            this.valueDeserializer = valueDeserializer;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
                throws JsonMappingException {
            JavaType type = context.getContextualType() != null || property == null
                    ? context.getContextualType()
                    : property.getType();
            JavaType valueType = type == null ? context.constructType(Object.class) : type.containedTypeOrUnknown(0);
            return new ArgumentValueDeserializer(context.findContextualValueDeserializer(valueType, property));
        }

        @Override
        public ArgumentValue deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (valueDeserializer == null) {
                return (ArgumentValue) context.handleUnexpectedToken(ArgumentValue.class, parser);
            }
            return ArgumentValue.ofNullable(valueDeserializer.deserialize(parser, context));
        }

        @Override
        public ArgumentValue getNullValue(DeserializationContext context) {
            return ArgumentValue.ofNullable(null);
        }

        @Override
        public Object getAbsentValue(DeserializationContext context) {
            return ArgumentValue.omitted();
        }

    }

}
