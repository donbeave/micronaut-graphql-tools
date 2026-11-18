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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.IntFunction;

/**
 * The {@link ValueConverter}s that need no input class: scalars, enums and lists. Built once per argument or input field
 * at startup.
 *
 * @since 1.0.0
 */
@Internal
public final class ValueConverters {

    /**
     * The value is already the declared type: a {@code String}, a {@code Boolean}, an {@code Integer} (an {@code int}
     * parameter takes it unboxed) and the other values graphql-java coerces to exactly the declared class.
     */
    public static final ValueConverter IDENTITY = value -> value;

    private static final ValueConverter FLOAT = value -> value == null || value instanceof Float
            ? value : (Object) ((Number) value).floatValue();

    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            char.class, Character.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class
    );

    private ValueConverters() {
    }

    /**
     * @param declared a class a built-in scalar maps to
     * @return the converter: graphql-java coerces Float to a {@code Double}, so only a {@code float} needs a conversion
     */
    public static ValueConverter builtIn(Class<?> declared) {
        return declared == float.class || declared == Float.class ? FLOAT : IDENTITY;
    }

    /**
     * @param declared          a class of a custom scalar's input classes
     * @param conversionService converts a value that is not an instance of the class (the application's
     *                          {@code TypeConverter} beans are registered in it)
     * @return the converter: a value of the declared class passes as is
     */
    public static ValueConverter scalar(Class<?> declared, ConversionService conversionService) {
        Class<?> boxed = WRAPPERS.getOrDefault(declared, declared);
        if (boxed == Object.class) {
            return IDENTITY;
        }
        Argument<?> target = Argument.of(boxed);
        return value -> value == null || boxed.isInstance(value) ? value : convertOrFail(conversionService, value, target);
    }

    /**
     * @param enumClass the Java enum
     * @return the converter: an enum constant passes as is, a name is looked up in a map built now
     */
    public static ValueConverter enumeration(Class<?> enumClass) {
        Map<String, Object> constants = new HashMap<>();
        for (Object constant : enumClass.getEnumConstants()) {
            constants.put(((Enum<?>) constant).name(), constant);
        }
        return value -> {
            if (value == null || enumClass.isInstance(value)) {
                return value;
            }
            Object constant = constants.get(String.valueOf(value));
            if (constant == null) {
                throw new IllegalArgumentException("`" + value + "` is not a constant of " + enumClass.getName());
            }
            return constant;
        };
    }

    /**
     * @param listClass the declared collection class
     * @return whether {@link #list} supports it: {@code Iterable}, {@code Collection}, {@code List}, {@code Set},
     * {@code SortedSet}, {@code Deque} and the classes that these collections instantiate
     */
    public static boolean isSupportedList(Class<?> listClass) {
        return factory(listClass) != null;
    }

    /**
     * @param listClass the declared collection class, one that {@link #isSupportedList} accepts
     * @param element   the converter of the elements
     * @return the converter: a new mutable collection, in the order of the list
     */
    public static ValueConverter list(Class<?> listClass, ValueConverter element) {
        IntFunction<Collection<Object>> factory = factory(listClass);
        ValueConverter converter = element == IDENTITY ? null : element;
        return value -> {
            if (value == null) {
                return null;
            }
            if (!(value instanceof Collection<?> source)) {
                throw new IllegalArgumentException("Expected a list, got " + value.getClass().getName());
            }
            Collection<Object> result = factory.apply(source.size());
            if (source instanceof List<?> list) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    result.add(converter == null ? list.get(i) : converter.convert(list.get(i)));
                }
            } else {
                for (Object item : source) {
                    result.add(converter == null ? item : converter.convert(item));
                }
            }
            return result;
        };
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static IntFunction<Collection<Object>> factory(Class<?> listClass) {
        if (listClass.isAssignableFrom(ArrayList.class)) {
            return ArrayList::new;
        }
        if (listClass.isAssignableFrom(LinkedHashSet.class)) {
            return LinkedHashSet::new;
        }
        if (listClass.isAssignableFrom(TreeSet.class)) {
            return size -> new TreeSet<>();
        }
        if (listClass.isAssignableFrom(LinkedList.class)) {
            return size -> new LinkedList<>();
        }
        return null;
    }

    /**
     * @param conversionService the service to ask
     * @param value             the value to convert
     * @param target            the declared type
     * @return the converted value
     * @throws IllegalArgumentException if no converter handles the pair
     */
    @Nullable
    public static Object convertOrFail(ConversionService conversionService, Object value, Argument<?> target) {
        Optional<?> converted = conversionService.convert(value, target);
        if (converted.isEmpty()) {
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " `" + value
                    + "` to " + target.getTypeString(false) + ": register a TypeConverter bean for the pair");
        }
        return converted.get();
    }

}
