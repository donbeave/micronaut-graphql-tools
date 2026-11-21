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
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

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

    @Factory
    @Requires(property = "tck.scenario", value = "constraint-violations")
    public static class Scalars {

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

        public String several(@NotBlank String name, @Positive int count, @Valid Cart cart) {
            return name;
        }
    }

}
