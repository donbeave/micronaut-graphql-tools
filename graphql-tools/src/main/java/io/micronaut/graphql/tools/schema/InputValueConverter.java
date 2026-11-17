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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.ArgumentValue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Converts the argument values graphql-java coerced (scalar values, enum names, a {@code Map} per input object and a
 * {@code List} per list) into the declared Java types of resolver parameters, with their type arguments.
 *
 * @since 1.0.0
 */
@Internal
public final class InputValueConverter {

    private final ObjectMapper objectMapper;
    private final Map<Class<?>, OmissionPlan> omissionPlans;

    /**
     * @param objectMapper  the mapper that converts the values
     * @param omissionPlans per input class, the properties to visit after a conversion to set omitted
     *                      {@link ArgumentValue} properties to {@link ArgumentValue#omitted()}; the binding fills the
     *                      map while it processes the schema, before the first request, and leaves it empty unless
     *                      omission detection is enabled
     */
    public InputValueConverter(ObjectMapper objectMapper, Map<Class<?>, OmissionPlan> omissionPlans) {
        this.objectMapper = objectMapper;
        this.omissionPlans = omissionPlans;
    }

    /**
     * @return the mapper that converts the values
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * @param argument a declared Java type with its type arguments, e.g. {@code List<List<SomeInput>>}
     * @return the Jackson type with the same type arguments
     */
    public JavaType toJavaType(Argument<?> argument) {
        Argument<?>[] typeParameters = argument.getTypeParameters();
        if (typeParameters.length == 0) {
            return objectMapper.getTypeFactory().constructType(argument.getType());
        }
        JavaType[] parameterTypes = Arrays.stream(typeParameters)
                .map(this::toJavaType)
                .toArray(JavaType[]::new);
        return objectMapper.getTypeFactory().constructParametricType(argument.getType(), parameterTypes);
    }

    /**
     * Converts a value into the declared Java type, including its type arguments. A value that already is an instance
     * of a declared type without type arguments is passed as is.
     *
     * @param value    the value graphql-java coerced
     * @param javaType the declared type
     * @return the converted value
     */
    @Nullable
    public Object convert(@Nullable Object value, JavaType javaType) {
        if (value == null) {
            return null;
        }
        if (!javaType.hasGenericTypes() && javaType.getRawClass().isInstance(value)) {
            return value;
        }
        Object converted = objectMapper.convertValue(value, javaType);
        if (!omissionPlans.isEmpty()) {
            fillOmitted(converted);
        }
        return converted;
    }

    /**
     * Visits only what the plans name: no other getter of an input class is called.
     */
    private void fillOmitted(@Nullable Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof ArgumentValue<?> argumentValue) {
            fillOmitted(argumentValue.getValue());
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                fillOmitted(item);
            }
        } else {
            OmissionPlan plan = omissionPlans.get(value.getClass());
            if (plan == null) {
                return;
            }
            for (BeanProperty<Object, Object> property : plan.argumentValueProperties()) {
                Object propertyValue = property.get(value);
                if (propertyValue == null) {
                    property.set(value, ArgumentValue.omitted());
                } else {
                    fillOmitted(propertyValue);
                }
            }
            for (BeanProperty<Object, Object> property : plan.inputObjectProperties()) {
                fillOmitted(property.get(value));
            }
        }
    }

    /**
     * The properties of one input class that omission detection visits, computed at startup.
     *
     * @param argumentValueProperties the readable and writable properties declared as {@link ArgumentValue}
     * @param inputObjectProperties   the readable properties whose GraphQL type is an input object type or a list of
     *                                one (they may hold {@link ArgumentValue} properties further down)
     */
    public record OmissionPlan(List<BeanProperty<Object, Object>> argumentValueProperties,
                               List<BeanProperty<Object, Object>> inputObjectProperties) {

        /**
         * @param argumentValueProperties the readable and writable properties declared as {@link ArgumentValue}
         * @param inputObjectProperties   the readable properties whose GraphQL type is an input object type or a
         *                                list of one
         */
        public OmissionPlan {
            argumentValueProperties = List.copyOf(argumentValueProperties);
            inputObjectProperties = List.copyOf(inputObjectProperties);
        }

    }

}
