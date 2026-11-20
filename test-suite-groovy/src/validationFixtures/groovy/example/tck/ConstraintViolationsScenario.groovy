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
package example.tck

import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping
import jakarta.inject.Singleton
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

// The fixtures of the TCK scenario `constraint-violations`: resolvers with constrained parameters and constrained input
// classes, validated by a real Micronaut Validation run (#468).

// tag::constrained-input[]
@CompileStatic
@GraphQLInput
class Line {
    @NotBlank
    String sku

    @Positive
    int quantity
}

@CompileStatic
@GraphQLInput
class Cart {
    @NotBlank
    String owner

    @Positive
    int quantity

    @Valid
    List<Line> lines
}
// end::constrained-input[]

@CompileStatic
@Factory
@Requires(property = 'tck.scenario', value = 'constraint-violations')
class ConstraintViolationsScalars {

    @Bean
    @Singleton
    GraphQLScalarMapping limits() {
        return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name('Limits').coercing(new Coercing<Object, Object>() {
            @Override
            Object serialize(Object value) {
                return value
            }

            @Override
            Object parseValue(Object value) {
                return value
            }

            @Override
            Object parseLiteral(Object value) {
                throw new UnsupportedOperationException()
            }
        }).build()).outputClasses(Map).inputClasses(Map).build()
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'constraint-violations')
class ConstraintViolationsQueries {

    String fine() {
        return 'fine'
    }

    // tag::constrained-parameter[]
    String rename(@NotBlank String name) {
        return name
    }

    String checkout(@Valid Cart cart) {
        return cart.owner
    }
    // end::constrained-parameter[]

    String restock(@Valid List<Line> lines) {
        return lines.size().toString()
    }

    String cap(Map<String, @Positive Integer> limits) {
        return limits.toString()
    }

    String several(@NotBlank String name, @Positive int count, @Valid Cart cart) {
        return name
    }
}
