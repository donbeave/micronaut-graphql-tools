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
package io.micronaut.graphql.tools.internal.input;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.ArgumentValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the instance of an input class from the {@code Map} that graphql-java coerced for an input object: the class is
 * instantiated through its introspected constructor, then every input field that the map holds and the constructor did
 * not take is set through its {@link BeanProperty}. A field the map lacks is left alone, except an {@link ArgumentValue}
 * field, which becomes {@link ArgumentValue#omitted()}. One converter per input type, built at startup and immutable
 * once built: a {@link Builder} collects the fields while the schema is bound, before the first request.
 *
 * @since 1.0.0
 */
@Internal
public final class InputObjectConverter implements ValueConverter {

    private static final Object ABSENT = new Object();

    private final Class<?> type;
    private final BeanIntrospection<Object> introspection;
    private final Object[] constructorDefaults;
    private final Slot[] constructorSlots;
    private final Slot[] propertySlots;

    private InputObjectConverter(Builder builder) {
        this.introspection = builder.introspection;
        this.type = builder.introspection.getBeanType();
        Argument<?>[] arguments = builder.constructorArguments;
        this.constructorDefaults = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            constructorDefaults[i] = defaultValue(arguments[i].getType());
        }
        this.constructorSlots = builder.pending.stream().filter(it -> it.index >= 0).toArray(Slot[]::new);
        this.propertySlots = builder.pending.stream().filter(it -> it.index < 0).toArray(Slot[]::new);
    }

    /**
     * @param introspection the introspection of the input class
     * @return a builder that collects the input fields; {@link Builder#build()} produces the immutable converter
     */
    public static Builder builder(BeanIntrospection<Object> introspection) {
        return new Builder(introspection);
    }

    @Override
    @Nullable
    public Object convert(@Nullable Object value) {
        if (value == null || type.isInstance(value)) {
            return value;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new InputConversionException("expected an input object", "Expected an input object for "
                    + type.getName() + ", got " + value.getClass().getName(), null);
        }
        Object bean;
        if (constructorDefaults.length == 0) {
            bean = introspection.instantiate();
        } else {
            Object[] arguments = constructorDefaults.clone();
            for (Slot slot : constructorSlots) {
                Object converted = slot.read(map);
                if (converted != ABSENT) {
                    arguments[slot.index] = converted;
                }
            }
            bean = introspection.instantiate(false, arguments);
        }
        for (Slot slot : propertySlots) {
            Object converted = slot.read(map);
            if (converted != ABSENT) {
                slot.property.set(bean, converted);
            }
        }
        return bean;
    }

    @Nullable
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        return 0d;
    }

    /**
     * Collects the input fields while the schema is bound. Runs at startup only; the converter it builds is immutable.
     */
    public static final class Builder {

        private final BeanIntrospection<Object> introspection;
        private final Argument<?>[] constructorArguments;
        private final List<Slot> pending = new ArrayList<>();

        private Builder(BeanIntrospection<Object> introspection) {
            this.introspection = introspection;
            this.constructorArguments = introspection.getConstructorArguments();
        }

        /**
         * Adds an input field.
         *
         * @param name          the name of the input field, which is the name of the property
         * @param property      the property that holds it
         * @param converter     the converter of its value
         * @param argumentValue whether the property is an {@link ArgumentValue}
         */
        public void addField(String name, BeanProperty<Object, Object> property, ValueConverter converter,
                             boolean argumentValue) {
            int index = -1;
            for (int i = 0; i < constructorArguments.length; i++) {
                if (constructorArguments[i].getName().equals(name)) {
                    index = i;
                }
            }
            Class<?> target = index >= 0 ? constructorArguments[index].getType() : property.getType();
            pending.add(new Slot(name, property, converter == ValueConverters.IDENTITY ? null : converter,
                    argumentValue, target.isPrimitive(), index));
        }

        /**
         * @return the converter, ready for requests
         */
        public InputObjectConverter build() {
            return new InputObjectConverter(this);
        }
    }

    private record Slot(String name, BeanProperty<Object, Object> property, @Nullable ValueConverter converter,
                        boolean argumentValue, boolean primitive, int index) {

        Object read(Map<?, ?> map) {
            Object raw = map.get(name);
            if (raw == null && !map.containsKey(name)) {
                return argumentValue ? ArgumentValue.omitted() : ABSENT;
            }
            Object converted;
            try {
                converted = raw == null || converter == null ? raw : converter.convert(raw);
            } catch (InputConversionException e) {
                throw e.within(name);
            }
            if (argumentValue) {
                return ArgumentValue.ofNullable(converted);
            }
            return converted == null && primitive ? ABSENT : converted;
        }
    }

}
