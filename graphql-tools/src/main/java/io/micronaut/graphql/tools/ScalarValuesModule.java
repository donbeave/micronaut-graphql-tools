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
package io.micronaut.graphql.tools;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.micronaut.core.annotation.Internal;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Internal
final class ScalarValuesModule extends SimpleModule {

    ScalarValuesModule(Collection<GraphQLScalarMapping> scalarMappings) {
        super("micronaut-graphql-tools-scalar-values");
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (GraphQLScalarMapping scalarMapping : scalarMappings) {
            for (Class<?> inputClass : scalarMapping.getInputClasses()) {
                if (!isConvertedByJackson(inputClass)) {
                    classes.add(inputClass);
                }
            }
        }
        for (Class<?> clazz : classes) {
            addPassThrough(clazz);
        }
    }

    static boolean isConvertedByJackson(Class<?> clazz) {
        return clazz == Object.class
                || clazz.isPrimitive()
                || clazz.isArray()
                || clazz.isEnum()
                || CharSequence.class.isAssignableFrom(clazz)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class == clazz
                || Character.class == clazz
                || Map.class.isAssignableFrom(clazz)
                || Iterable.class.isAssignableFrom(clazz);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addPassThrough(Class<?> clazz) {
        addSerializer((Class) clazz, new PassThroughSerializer(clazz));
        addDeserializer((Class) clazz, new PassThroughDeserializer(clazz));
    }

    private static final class PassThroughSerializer extends StdSerializer<Object> {

        @SuppressWarnings("unchecked")
        PassThroughSerializer(Class<?> clazz) {
            super((Class<Object>) clazz);
        }

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeEmbeddedObject(value);
        }

    }

    private static final class PassThroughDeserializer extends StdDeserializer<Object> {

        PassThroughDeserializer(Class<?> clazz) {
            super(clazz);
        }

        @Override
        public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_EMBEDDED_OBJECT)) {
                Object value = parser.getEmbeddedObject();
                if (handledType().isInstance(value)) {
                    return value;
                }
            }
            return context.handleUnexpectedToken(handledType(), parser);
        }

    }

}
