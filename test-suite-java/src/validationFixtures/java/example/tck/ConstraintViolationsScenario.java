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
package example.tck;

import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import jakarta.inject.Singleton;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * The fixtures of the TCK scenario {@code constraint-violations}: resolvers with constrained parameters and constrained input
 * classes, validated by a real Micronaut Validation run (#468).
 */
public final class ConstraintViolationsScenario {

    private ConstraintViolationsScenario() {
    }

    // tag::constrained-input[]
    @GraphQLInput
    public static class Line {
        @NotBlank
        private String sku;
        @Positive
        private int quantity;

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    @GraphQLInput
    public static class Cart {
        @NotBlank
        private String owner;
        @Positive
        private int quantity;
        @Valid
        private List<Line> lines;

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public List<Line> getLines() {
            return lines;
        }

        public void setLines(List<Line> lines) {
            this.lines = lines;
        }
    }
    // end::constrained-input[]

    @Retention(RetentionPolicy.RUNTIME)
    @Target(java.lang.annotation.ElementType.TYPE)
    @Constraint(validatedBy = {})
    public @interface OrderedSpan {
        String message() default "min must not exceed max";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    @GraphQLInput
    @OrderedSpan
    public static class Span {
        private int min;
        private int max;

        public int getMin() {
            return min;
        }

        public void setMin(int min) {
            this.min = min;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = "constraint-violations")
    public static class OrderedSpanValidator implements ConstraintValidator<OrderedSpan, Span> {
        @Override
        public boolean isValid(Span value, AnnotationValue<OrderedSpan> annotation, ConstraintValidatorContext context) {
            return value == null || value.getMin() <= value.getMax();
        }
    }

    public enum Region {
        NORTH, SOUTH
    }

    public static final class Bag<T> {
        private final List<T> items;

        public Bag(List<T> items) {
            this.items = items;
        }

        public List<T> getItems() {
            return items;
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = "constraint-violations")
    public static class BagExtractor implements ValueExtractor<Bag<@ExtractedValue ?>> {
        @Override
        public void extractValues(Bag<?> bag, ValueReceiver receiver) {
            int index = 0;
            for (Object item : bag.getItems()) {
                receiver.indexedValue("item", index++, item);
            }
        }
    }

    @Factory
    @Requires(property = "tck.scenario", value = "constraint-violations")
    public static class Scalars {

        @Bean
        @Singleton
        GraphQLScalarMapping spans() {
            return scalar("Spans", NavigableMap.class, input -> {
                NavigableMap<String, Span> spans = new TreeMap<>();
                input.forEach((key, value) -> {
                    Map<?, ?> bounds = (Map<?, ?>) value;
                    Span span = new Span();
                    span.setMin(((Number) bounds.get("min")).intValue());
                    span.setMax(((Number) bounds.get("max")).intValue());
                    spans.put(key, span);
                });
                return spans;
            });
        }

        @Bean
        @Singleton
        GraphQLScalarMapping weights() {
            return scalar("Weights", SortedMap.class, input -> {
                SortedMap<UUID, Integer> weights = new TreeMap<>();
                input.forEach((key, value) -> weights.put(UUID.fromString(key), ((Number) value).intValue()));
                return weights;
            });
        }

        @Bean
        @Singleton
        GraphQLScalarMapping tally() {
            return scalar("Tally", ConcurrentMap.class, input -> {
                ConcurrentMap<Region, Integer> tally = new ConcurrentHashMap<>();
                input.forEach((key, value) -> tally.put(Region.valueOf(key), ((Number) value).intValue()));
                return tally;
            });
        }

        @Bean
        @Singleton
        GraphQLScalarMapping pack() {
            return scalar("Pack", Bag.class, input -> {
                List<Object> items = new java.util.ArrayList<>();
                ((List<?>) input.get("items")).forEach(items::add);
                return new Bag<>(items);
            });
        }

        @SuppressWarnings("unchecked")
        private static GraphQLScalarMapping scalar(String name, Class<?> type, Function<Map<String, Object>, Object> parse) {
            return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name(name).coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object value) {
                    return value;
                }

                @Override
                public Object parseValue(Object value) {
                    return parse.apply((Map<String, Object>) value);
                }

                @Override
                public Object parseLiteral(Object value) {
                    throw new UnsupportedOperationException();
                }
            }).build()).outputClasses(type).inputClasses(type).build();
        }

        @Bean
        @Singleton
        GraphQLScalarMapping limits() {
            return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name("Limits").coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object value) {
                    return value;
                }

                @Override
                public Object parseValue(Object value) {
                    return value;
                }

                @Override
                public Object parseLiteral(Object value) {
                    throw new UnsupportedOperationException();
                }
            }).build()).outputClasses(Map.class).inputClasses(Map.class).build();
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "constraint-violations")
    public static class Queries {

        public String fine() {
            return "fine";
        }

        // tag::constrained-parameter[]
        public String rename(@NotBlank String name) {
            return name;
        }

        public String checkout(@Valid Cart cart) {
            return cart.getOwner();
        }
        // end::constrained-parameter[]

        public String restock(@Valid List<Line> lines) {
            return String.valueOf(lines.size());
        }

        public String cap(Map<String, @Positive Integer> limits) {
            return limits.toString();
        }

        public String spans(@Valid List<Span> spans) {
            return String.valueOf(spans.size());
        }

        public String spanMap(NavigableMap<String, @Valid Span> spans) {
            return String.valueOf(spans.size());
        }

        public String weights(SortedMap<UUID, @Positive Integer> weights) {
            return String.valueOf(weights.size());
        }

        public String tally(ConcurrentMap<Region, @Positive Integer> tally) {
            return String.valueOf(tally.size());
        }

        public String pack(Bag<@Positive Integer> pack) {
            return String.valueOf(pack.getItems().size());
        }

        public String several(@NotBlank String name, @Positive int count, @Valid Cart cart) {
            return name;
        }
    }

}
