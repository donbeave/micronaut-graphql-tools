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
import graphql.GraphQLError
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.graphql.tools.error.GraphQLExceptionContext
import io.micronaut.graphql.tools.error.GraphQLExceptionMapper
import jakarta.validation.ConstraintViolationException
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.mapping.GraphQLScalarMapping
import jakarta.inject.Singleton
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext
import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.valueextraction.ExtractedValue
import jakarta.validation.valueextraction.ValueExtractor

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Function

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

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Constraint(validatedBy = [])
@interface OrderedSpan {
    String message() default 'min must not exceed max'

    Class<?>[] groups() default []

    Class<? extends Payload>[] payload() default []
}

@CompileStatic
@GraphQLInput
@OrderedSpan
class Span {
    int min
    int max
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'constraint-violations')
class OrderedSpanValidator implements ConstraintValidator<OrderedSpan, Span> {
    @Override
    boolean isValid(Span value, AnnotationValue<OrderedSpan> annotation, ConstraintValidatorContext context) {
        return value == null || value.min <= value.max
    }
}

enum Region {
    NORTH, SOUTH
}

@CompileStatic
final class Bag<T> {
    final List<T> items

    Bag(List<T> items) {
        this.items = items
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'constraint-violations')
class BagExtractor implements ValueExtractor<Bag<@ExtractedValue ?>> {
    @Override
    void extractValues(Bag<?> bag, ValueExtractor.ValueReceiver receiver) {
        int index = 0
        for (Object item : bag.items) {
            receiver.indexedValue('item', index++, item)
        }
    }
}

@CompileStatic
final class Tray<T> {
    final List<T> items

    Tray(List<T> items) {
        this.items = items
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'constraint-violations')
class TrayExtractor implements ValueExtractor<Tray<@ExtractedValue ?>> {
    @Override
    void extractValues(Tray<?> tray, ValueExtractor.ValueReceiver receiver) {
        int index = 0
        for (Object item : tray.items) {
            receiver.indexedValue('<tray item>', index++, item)
        }
    }
}

@CompileStatic
@Factory
@Requires(property = 'tck.scenario', value = 'constraint-violations')
class ConstraintViolationsScalars {

    @Bean
    @Singleton
    GraphQLScalarMapping spans() {
        return scalar('Spans', NavigableMap) { Map<String, Object> input ->
            NavigableMap<String, Span> spans = new TreeMap<>()
            input.each { String key, Object value ->
                Map<String, Object> bounds = (Map<String, Object>) value
                spans.put(key, new Span(min: ((Number) bounds.get('min')).intValue(), max: ((Number) bounds.get('max')).intValue()))
            }
            return spans
        }
    }

    @Bean
    @Singleton
    GraphQLScalarMapping weights() {
        return scalar('Weights', SortedMap) { Map<String, Object> input ->
            SortedMap<UUID, Integer> weights = new TreeMap<>()
            input.each { String key, Object value -> weights.put(UUID.fromString(key), ((Number) value).intValue()) }
            return weights
        }
    }

    @Bean
    @Singleton
    GraphQLScalarMapping tally() {
        return scalar('Tally', ConcurrentMap) { Map<String, Object> input ->
            ConcurrentMap<Region, Integer> tally = new ConcurrentHashMap<>()
            input.each { String key, Object value -> tally.put(Region.valueOf(key), ((Number) value).intValue()) }
            return tally
        }
    }

    @Bean
    @Singleton
    GraphQLScalarMapping pack() {
        return scalar('Pack', Bag) { Map<String, Object> input ->
            return new Bag<Object>(new ArrayList<Object>((List<Object>) input.get('items')))
        }
    }

    @Bean
    @Singleton
    GraphQLScalarMapping tray() {
        return scalar('Tray', Tray) { Map<String, Object> input ->
            return new Tray<Object>(new ArrayList<Object>((List<Object>) input.get('items')))
        }
    }

    private static GraphQLScalarMapping scalar(String name, Class<?> type, Function<Map<String, Object>, Object> parse) {
        return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name(name).coercing(new Coercing<Object, Object>() {
            @Override
            Object serialize(Object value) {
                return value
            }

            @Override
            Object parseValue(Object value) {
                return parse.apply((Map<String, Object>) value)
            }

            @Override
            Object parseLiteral(Object value) {
                throw new UnsupportedOperationException()
            }
        }).build()).outputClasses(type).inputClasses(type).build()
    }

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

    String spans(@Valid List<Span> spans) {
        return spans.size().toString()
    }

    String spanMap(NavigableMap<String, @Valid Span> spans) {
        return spans.size().toString()
    }

    String weights(SortedMap<UUID, @Positive Integer> weights) {
        return weights.size().toString()
    }

    String tally(ConcurrentMap<Region, @Positive Integer> tally) {
        return tally.size().toString()
    }

    String pack(Bag<@Positive Integer> pack) {
        return pack.items.size().toString()
    }

    String tray(Tray<@Positive Integer> tray) {
        return tray.items.size().toString()
    }

    String several(@NotBlank String name, @Positive int count, @Valid Cart cart) {
        return name
    }
}

// tag::custom-mapper[]
@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'constraint-violation-mapper')
class InvalidInputMapper implements GraphQLExceptionMapper<ConstraintViolationException> {
    @Override
    GraphQLError map(ConstraintViolationException exception, GraphQLExceptionContext context) {
        List<String> fields = exception.constraintViolations.collect { it.propertyPath.last().name }.sort()
        return context.error('The input is not valid', [code: 'INVALID_INPUT', fields: fields] as Map<String, Object>)
    }
}
// end::custom-mapper[]

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'constraint-violation-mapper')
class RegistrationQueries {

    String register(@NotBlank String name, @Positive int seats) {
        return name
    }
}
