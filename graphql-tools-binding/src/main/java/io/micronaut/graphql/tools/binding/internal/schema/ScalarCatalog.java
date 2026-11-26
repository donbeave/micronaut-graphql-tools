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
package io.micronaut.graphql.tools.binding.internal.schema;

import graphql.language.ListType;
import graphql.language.TypeName;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

/**
 * The scalars that the library knows by name and the Java classes each of them accepts: the GraphQL built-ins, the built-in
 * extensions and the standard scalars of the scalars module. It is the one table that the runtime and the compile-time checks
 * share. The test of the scalars module fails when a standard scalar has no entry in {@link #getStandardScalars()}.
 *
 * @author Alexey Zhokhov
 */
@Internal
public final class ScalarCatalog {

    private static final String GSTRING = "groovy.lang.GString";

    private static final Map<String, Set<Class<?>>> SYSTEM_TYPES = new HashMap<>();

    private static final Map<String, Class<?>> STANDARD_SCALARS = new HashMap<>();

    // https://www.graphql-java.com/documentation/scalars
    static {
        SYSTEM_TYPES.put("String", unmodifiableSet(new HashSet<>(Collections.singletonList(String.class))));
        SYSTEM_TYPES.put("Boolean", unmodifiableSet(new HashSet<>(asList(boolean.class, Boolean.class))));
        SYSTEM_TYPES.put("Int", unmodifiableSet(new HashSet<>(asList(int.class, Integer.class))));
        // GraphQL Float is a signed double-precision value; graphql-java coerces it to Double
        SYSTEM_TYPES.put("Float", unmodifiableSet(new HashSet<>(asList(float.class, Float.class, double.class, Double.class))));
        SYSTEM_TYPES.put("ID", unmodifiableSet(new HashSet<>(Collections.singletonList(String.class))));

        SYSTEM_TYPES.put("Long", unmodifiableSet(new HashSet<>(asList(long.class, Long.class))));
        SYSTEM_TYPES.put("Short", unmodifiableSet(new HashSet<>(asList(short.class, Short.class))));
        SYSTEM_TYPES.put("BigDecimal", unmodifiableSet(new HashSet<>(Collections.singletonList(BigDecimal.class))));
        SYSTEM_TYPES.put("BigInteger", unmodifiableSet(new HashSet<>(Collections.singletonList(BigInteger.class))));

        STANDARD_SCALARS.put("Instant", Instant.class);
        STANDARD_SCALARS.put("LocalDate", LocalDate.class);
        STANDARD_SCALARS.put("LocalTime", LocalTime.class);
        STANDARD_SCALARS.put("LocalDateTime", LocalDateTime.class);
        STANDARD_SCALARS.put("OffsetTime", OffsetTime.class);
        STANDARD_SCALARS.put("OffsetDateTime", OffsetDateTime.class);
        STANDARD_SCALARS.put("ZonedDateTime", ZonedDateTime.class);
        STANDARD_SCALARS.put("YearMonth", YearMonth.class);
        STANDARD_SCALARS.put("Year", Year.class);
        STANDARD_SCALARS.put("Duration", Duration.class);
        STANDARD_SCALARS.put("Period", Period.class);
        STANDARD_SCALARS.put("JSON", Object.class);
        STANDARD_SCALARS.put("Url", URL.class);
        STANDARD_SCALARS.put("UUID", UUID.class);
        STANDARD_SCALARS.put("Locale", Locale.class);
        STANDARD_SCALARS.put("Currency", Currency.class);
        STANDARD_SCALARS.put("PositiveInt", Integer.class);
        STANDARD_SCALARS.put("NegativeInt", Integer.class);
        STANDARD_SCALARS.put("NonPositiveInt", Integer.class);
        STANDARD_SCALARS.put("NonNegativeInt", Integer.class);
        STANDARD_SCALARS.put("PositiveFloat", Double.class);
        STANDARD_SCALARS.put("NegativeFloat", Double.class);
        STANDARD_SCALARS.put("NonPositiveFloat", Double.class);
        STANDARD_SCALARS.put("NonNegativeFloat", Double.class);
    }

    private static final Map<String, Class<?>> STANDARD_SCALAR_CLASSES = unmodifiableMap(STANDARD_SCALARS);

    private static final Set<Class<?>> SYSTEM_TYPE_CLASSES = SYSTEM_TYPES.values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

    private ScalarCatalog() {
    }

    /**
     * @return the standard scalars of the scalars module, each name with the Java class that stands for it
     */
    public static Map<String, Class<?>> getStandardScalars() {
        return STANDARD_SCALAR_CLASSES;
    }

    public static Set<Class<?>> getSupportedClasses(@NonNull TypeName typeName) {
        ArgumentUtils.requireNonNull("typeName", typeName);
        return SYSTEM_TYPES.get(typeName.getName());
    }

    public static Set<Class<?>> getSupportedClasses(@NonNull ListType listType) {
        ArgumentUtils.requireNonNull("listType", listType);
        return unmodifiableSet(new HashSet<>(asList(
                Iterable.class,
                Collection.class,
                List.class,
                Set.class
        )));
    }

    public static boolean isGraphQlBuiltInType(@NonNull TypeName typeName) {
        ArgumentUtils.requireNonNull("typeName", typeName);
        return SYSTEM_TYPES.containsKey(typeName.getName());
    }

    /**
     * @param typeName the name of a scalar
     * @return whether the scalar is built in: one of GraphQL's own scalars or an extended scalar the library registers
     */
    public static boolean isGraphQlBuiltInType(@NonNull String typeName) {
        ArgumentUtils.requireNonNull("typeName", typeName);
        return SYSTEM_TYPES.containsKey(typeName);
    }

    /**
     * Whether a field of the scalar may declare {@code returnClass} as its Java type.
     *
     * <p>A field of {@code String} may declare Groovy's {@code GString}: graphql-java's {@code String} scalar serializes it with
     * {@code toString()}, so the library adds no conversion step. It is matched by name, so the library has no Groovy
     * dependency. {@code ID} does not take it: graphql-java's {@code ID} scalar rejects every class but a few.</p>
     *
     * @param typeName    the name of a built-in scalar
     * @param returnClass the declared Java type of the field
     * @return whether the class is accepted
     */
    public static boolean acceptsReturnClass(@NonNull String typeName, @NonNull Class<?> returnClass) {
        Set<Class<?>> supported = SYSTEM_TYPES.get(typeName);
        return supported != null && (supported.contains(returnClass)
                || "String".equals(typeName) && GSTRING.equals(returnClass.getName()));
    }

    public static boolean isJavaBuiltInClass(@NonNull Class<?> clazz) {
        ArgumentUtils.requireNonNull("clazz", clazz);
        return SYSTEM_TYPE_CLASSES.contains(clazz);
    }

}
