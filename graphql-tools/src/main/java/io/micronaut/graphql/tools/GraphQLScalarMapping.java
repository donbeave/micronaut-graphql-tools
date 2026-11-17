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

import graphql.schema.GraphQLScalarType;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps a custom GraphQL scalar of the schema to Java: the graphql-java {@link GraphQLScalarType} that implements it,
 * and the Java classes that may stand for it. Register one bean of this type per custom scalar, e.g. in a
 * {@code @Factory}:
 *
 * <pre>
 * &#64;Bean
 * &#64;Singleton
 * GraphQLScalarMapping localDate() {
 *     return GraphQLScalarMapping.of(myLocalDateScalarType, LocalDate.class);
 * }
 * </pre>
 *
 * <ul>
 *     <li><b>Output classes</b> are the classes the scalar's {@code serialize} accepts. A field of the scalar type must
 *     be backed by one of them or a subclass.</li>
 *     <li><b>Input classes</b> are the classes the scalar's {@code parseValue}/{@code parseLiteral} produce. An argument
 *     or an input object field of the scalar type must be declared as exactly one of them.</li>
 * </ul>
 *
 * A primitive Java type matches its wrapper class. A scalar of the schema that a field, argument or input field uses
 * without a mapping fails the startup, as does a Java class outside the declared ones.
 *
 * @since 1.0.0
 */
public final class GraphQLScalarMapping {

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

    private final GraphQLScalarType scalarType;
    private final Set<Class<?>> outputClasses;
    private final Set<Class<?>> inputClasses;

    private GraphQLScalarMapping(GraphQLScalarType scalarType, Set<Class<?>> outputClasses,
                                 Set<Class<?>> inputClasses) {
        this.scalarType = scalarType;
        this.outputClasses = outputClasses;
        this.inputClasses = inputClasses;
    }

    /**
     * Maps a scalar to the same Java classes for output and input.
     *
     * @param scalarType  the scalar implementation; its name is the scalar's name in the schema
     * @param javaClass   a Java class that stands for the scalar
     * @param moreClasses further Java classes that stand for the scalar
     * @return the mapping
     */
    @NonNull
    public static GraphQLScalarMapping of(@NonNull GraphQLScalarType scalarType, @NonNull Class<?> javaClass,
                                          @NonNull Class<?>... moreClasses) {
        ArgumentUtils.requireNonNull("javaClass", javaClass);
        ArgumentUtils.requireNonNull("moreClasses", moreClasses);
        Class<?>[] classes = new Class<?>[moreClasses.length + 1];
        classes[0] = javaClass;
        System.arraycopy(moreClasses, 0, classes, 1, moreClasses.length);
        return builder(scalarType).outputClasses(classes).inputClasses(classes).build();
    }

    /**
     * Starts a mapping with different output and input classes.
     *
     * @param scalarType the scalar implementation; its name is the scalar's name in the schema
     * @return the builder
     */
    @NonNull
    public static Builder builder(@NonNull GraphQLScalarType scalarType) {
        ArgumentUtils.requireNonNull("scalarType", scalarType);
        return new Builder(scalarType);
    }

    /**
     * @return the name of the scalar in the schema
     */
    @NonNull
    public String getName() {
        return scalarType.getName();
    }

    /**
     * @return the scalar implementation
     */
    @NonNull
    public GraphQLScalarType getScalarType() {
        return scalarType;
    }

    /**
     * @return the classes the scalar serializes (primitives are given as their wrapper classes)
     */
    @NonNull
    public Set<Class<?>> getOutputClasses() {
        return outputClasses;
    }

    /**
     * @return the classes the scalar parses input values into (primitives are given as their wrapper classes)
     */
    @NonNull
    public Set<Class<?>> getInputClasses() {
        return inputClasses;
    }

    /**
     * @param javaClass the declared Java type of a field
     * @return whether the scalar can serialize values of this type: it is an output class or a subclass of one
     */
    public boolean isOutputClass(@NonNull Class<?> javaClass) {
        Class<?> wrapped = wrap(javaClass);
        return outputClasses.stream().anyMatch(it -> it.isAssignableFrom(wrapped));
    }

    /**
     * @param javaClass the declared Java type of an argument or an input object field
     * @return whether the type is exactly one of the input classes
     */
    public boolean isInputClass(@NonNull Class<?> javaClass) {
        return inputClasses.contains(wrap(javaClass));
    }

    @Override
    public String toString() {
        return "GraphQLScalarMapping{name=" + getName() + ", outputClasses=" + outputClasses
                + ", inputClasses=" + inputClasses + '}';
    }

    static Class<?> wrap(Class<?> javaClass) {
        return WRAPPERS.getOrDefault(javaClass, javaClass);
    }

    private static Set<Class<?>> toSet(String name, Class<?>... classes) {
        ArgumentUtils.requireNonNull(name, classes);
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> clazz : Arrays.asList(classes)) {
            ArgumentUtils.requireNonNull(name, clazz);
            result.add(wrap(clazz));
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Builds a {@link GraphQLScalarMapping} whose output and input classes differ. At least one of the two sets must
     * not be empty; a scalar without input classes can only be used for fields, one without output classes only for
     * arguments and input fields.
     */
    public static final class Builder {

        private final GraphQLScalarType scalarType;
        private Set<Class<?>> outputClasses = Collections.emptySet();
        private Set<Class<?>> inputClasses = Collections.emptySet();

        private Builder(GraphQLScalarType scalarType) {
            this.scalarType = scalarType;
        }

        /**
         * @param classes the classes the scalar's {@code serialize} accepts; a field may be backed by any of them or
         *                a subclass
         * @return this builder
         */
        @NonNull
        public Builder outputClasses(@NonNull Class<?>... classes) {
            this.outputClasses = toSet("outputClasses", classes);
            return this;
        }

        /**
         * @param classes the classes the scalar's {@code parseValue}/{@code parseLiteral} produce; an argument or an
         *                input object field must be declared as exactly one of them
         * @return this builder
         */
        @NonNull
        public Builder inputClasses(@NonNull Class<?>... classes) {
            this.inputClasses = toSet("inputClasses", classes);
            return this;
        }

        /**
         * @return the mapping
         */
        @NonNull
        public GraphQLScalarMapping build() {
            if (outputClasses.isEmpty() && inputClasses.isEmpty()) {
                throw new IllegalArgumentException("The mapping of the scalar " + scalarType.getName()
                        + " declares neither output nor input classes.");
            }
            return new GraphQLScalarMapping(scalarType, outputClasses, inputClasses);
        }

    }

}
