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

/**
 * Carries the values that custom scalars parsed through the {@code ObjectMapper} conversion of input objects
 * unchanged.
 * <p>
 * graphql-java hands an input object to a data fetcher as a {@code Map} whose leaves are already coerced: a
 * {@code LocalDate} scalar yields a {@code LocalDate}. The library converts that map into the input class with Jackson,
 * which writes the map into a token buffer and reads the class back. Without this module a scalar value whose class
 * Jackson cannot (or not losslessly) serialize, such as {@code java.time} types without the JSR-310 module, fails the
 * request. The module writes each value of a declared input class as an embedded object and reads it back as the same
 * instance.
 * <p>
 * Classes Jackson already converts losslessly (strings, numbers, booleans, maps, collections, arrays, enums and
 * {@code Object}) are left alone, so that, for example, a JSON scalar's maps are still converted as maps.
 *
 * @since 1.0.0
 */
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
